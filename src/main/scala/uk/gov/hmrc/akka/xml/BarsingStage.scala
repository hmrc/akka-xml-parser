/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.akka.xml

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader, WFCException}
import com.fasterxml.aalto.stax.InputFactoryImpl
import uk.gov.hmrc.akka.xml.CompleteChunkStage.{OPENING_CHEVRON, STREAM_IS_EMPTY, STREAM_SIZE}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

/**
  * Parse an xml document as it is flowing through the system. By parsing we mean extracting/updating/deleting/validating certain elements
  * Parsing a big document is very resource intensive, so we provide the option to only parse the beginning part, and then
  * leave the rest of the document unparsed
  * Created by Gabor Dorcsinecz on 29/11/17.
  */
object BarsingStage {
  val MALFORMED_STATUS = "Malformed"
  val STREAM_MAX_SIZE = "Stream max size"
  val STREAM_IS_EMPTY = "Stream is empty"
  val STREAM_SIZE_LESS_AND_BELOW_MIN = "Stream Size"
  val STREAM_SIZE = "Stream Size"
  val NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE = "No validation tags were found in first n bytes failure"
  val VALIDATION_INSTRUCTION_FAILURE = "Validation instruction failure"
  val PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE = "Not all of the xml validations / checks were done"
  val XML_START_END_TAGS_MISMATCH = "Start and End tags mismatch. Element(s) - "
  val MAX_PARSE_LENGTH = Int.MaxValue //if no maximum validation size was given we will parse the whole document

  /**
    *
    * @param instructions
    * @param validationMaxSize
    * @param packageMaxSize
    * @return
    */
  def parser(instructions: Seq[XMLInstruction], validationMaxSize: Option[Int] = None, packageMaxSize: Option[Int] = None)
  : Flow[ByteString, (ByteString, Set[XMLElement]), NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(instructions, validationMaxSize))
  }

  private class StreamingXmlParser(instructions: Seq[XMLInstruction], validationMaxSize: Option[Int] = None)
    extends GraphStage[FlowShape[ByteString, (ByteString, Set[XMLElement])]]
      with StreamHelper
      with ParsingDataFunctions {
    val in: Inlet[ByteString] = Inlet("XMLParser.in")
    val out: Outlet[(ByteString, Set[XMLElement])] = Outlet("XMLParser.out")

    override val shape: FlowShape[ByteString, (ByteString, Set[XMLElement])] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        import javax.xml.stream.XMLStreamConstants

        private val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
        private val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = feeder.createAsyncFor(Array.empty)
        var parsingData = ByteString.empty   //Store the data here for parsing
        var isCharacterBuffering = false
        var chunkOffset = 0 //A pointer in the current data chunk
        var continueParsing = true   //When we reached our parsing length/target we don't want to parse anymore
        var elementBlockExtracting: Boolean = false
        var totalProcessedLength = 0   //How many bytes did we send out which almost always equals the length we received. We remove the BOM
        var isFirstChunk = true    //In case of the first chunk we try to remove the BOM

        val node = ArrayBuffer[String]()
        val completedInstructions = ArrayBuffer[XMLInstruction]()   //Gather the already parsed (e.g. extracted) stuff here
        val xmlElements = mutable.Set[XMLElement]()
        val bufferedText = new StringBuilder   //Buffer for xml Text nodes
        val streamBuffer = ArrayBuffer[Byte]()
        val incompleteBytes = ArrayBuffer[Byte]()   //xml tags or text are broken at package boudaries. We store the here to retry at the next iteration
        val elementBlock = new StringBuilder
        val validators = mutable.Map[XMLValidate, ArrayBuffer[Byte]]()

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            processStage(processOnPush)
          }

          override def onUpstreamFinish(): Unit = {
            parser.getInputFeeder.endOfInput()
            processStage(processOnUpstreamFinish)
            completeStage()
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        def processOnPush(): Try[Unit] = {
          var incomingData = grab(in)  //This is a var for a reason. We don't want to copy to another variable every time, when we only change it very few times

          if (isFirstChunk && incomingData.length > 0) { //Remove any Byte Order Mark from the file
            isFirstChunk = false
            val openingChevronAt = incomingData.indexOf(60.toByte) //Decimal 60 is < in all encodings
            if (openingChevronAt > 0) { //This file stream has a BOM (Byte Order Mark) at the beginning or it is not xml
              incomingData = incomingData.drop(openingChevronAt)
            }
          }
          chunkOffset = 0
          Try {
            totalProcessedLength += incomingData.length //incompleteBytes were already added to the total length earlier
            if (continueParsing) { //We want to parse the beginning of an xml, and not the rest.
              parser.getInputFeeder.feedInput(incomingData.toArray, 0, incomingData.length)
              parsingData = ByteString(incompleteBytes.toArray) ++ incomingData
              incompleteBytes.clear()
              advanceParser()
              push(out, (ByteString(streamBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
              streamBuffer.clear()

              if (totalProcessedLength > validationMaxSize.getOrElse(MAX_PARSE_LENGTH)) { //Stop parsing when the max length was reached
                continueParsing = false
                parser.getInputFeeder.endOfInput()
              }

              if (totalProcessedLength == 0) {
                throw new EmptyStreamError()
              }
              if (totalProcessedLength > validationMaxSize.getOrElse(MAX_PARSE_LENGTH) &&
                instructions.collect { case e: XMLValidate => e }.exists(!completedInstructions.contains(_))) {
                throw new NoValidationTagsFoundWithinFirstNBytesException
              }
            } else { //We parsed the beginning of the xml already, so let's just push the rest of the data through
              if (incompleteBytes.length > 0) {    //if we have incompleteBytes we must send them out too, which can happen just after parsing was finished
                incomingData = ByteString(incompleteBytes.toArray) ++ incomingData
                incompleteBytes.clear()
              }
              push(out, (incomingData, Set.empty[XMLElement]))
            }
          }
        }

        def processOnUpstreamFinish(): Try[Unit] = {
          for {
            _ <- Try {
              if (continueParsing)  //Only parse the remaining bytes if they are required
                advanceParser()
            }
            _ <- if ((instructions.count(x => x.isInstanceOf[XMLValidate]) > 0) &&
              (completedInstructions.count(x => x.isInstanceOf[XMLValidate]) != instructions.count(x => x.isInstanceOf[XMLValidate]))) {
              Failure(new IncompleteXMLValidationException)
            }
            else Success(Unit)
          } yield {
            emitStage()
          }
        }

        def processStage(f: () => Try[Unit]) = {
          f().recover {  //Execute the parsing function, then handle the possible errors
            case e: WFCException =>
              incompleteBytes.prependAll(parsingData.toArray)
              emitStage(XMLElement(Nil, Map(MALFORMED_STATUS -> e.getMessage), Some(MALFORMED_STATUS)))
              completeStage()
            case e: EmptyStreamError =>
              emitStage(
                XMLElement(Nil, Map.empty, Some(STREAM_IS_EMPTY)),
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
              )
              completeStage()
            case e: NoValidationTagsFoundWithinFirstNBytesException =>
              emitStage(
                XMLElement(Nil, Map(NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE -> ""), Some(NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE)),
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE)))
              completeStage()
            case e: IncompleteXMLValidationException =>
              emitStage(
                XMLElement(Nil, Map(PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE -> ""),
                  Some(PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE)))
              completeStage()
            case e: ParserValidationError =>
              emitStage(
                XMLElement(Nil, Map(VALIDATION_INSTRUCTION_FAILURE -> e.toString), Some(VALIDATION_INSTRUCTION_FAILURE)))
              completeStage()
            case e: Throwable =>
              throw e
          }
        }

        private def emitStage(elementsToAdd: XMLElement*) = {
          val streamSize = XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
          emit(out, (ByteString(incompleteBytes.toArray), getCompletedXMLElements(xmlElements).toSet ++ elementsToAdd + streamSize))
        }

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            val (start, end) = getBounds(parser)
            event match {

              case AsyncXMLStreamReader.EVENT_INCOMPLETE => //This can only happen at the end of the xml part
                val chunk = parsingData.slice(chunkOffset, parsingData.length)
                incompleteBytes ++= chunk

              case XMLStreamConstants.START_ELEMENT =>
                node += parser.getLocalName
                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => e match {
                  case e@XMLExtract(`node`, _, false) if ExtractNameSpace(parser, e.attributes).nonEmpty || e.attributes.isEmpty =>
                    val keys = ExtractNameSpace(parser, e.attributes)
                    val ele = XMLElement(e.xPath, keys, None)
                    xmlElements.add(ele)

                  case e@XMLExtract(_, _, true) if node.toList == e.xPath | elementBlockExtracting =>
                    elementBlockExtracting = true
                    elementBlock.append(extractBytes(parsingData, start, end).utf8String)

                  case e: XMLUpdate if e.xPath == node.slice(0, e.xPath.length) =>
                    e.xPath match {
                      case path if path == node.toList =>
                        val input = getUpdatedElement(e.xPath, e.attributes, e.upsertBlock(parser.getPrefix))(parser).getBytes
                        streamBuffer ++= insertBytes(parsingData, chunkOffset, start, input)
                        chunkOffset = end
                      case _ =>
                        chunkOffset = end
                    }

                  case e: XMLValidate if e.start == node.slice(0, e.start.length) =>
                    val newBytes = if (node == e.end) ArrayBuffer.empty else parsingData.slice(start, end)
                    if (!parser.isEmptyElement) {
                      val ele = validators.get(e) match {
                        case Some(x) => (e, x ++ newBytes)
                        case None => (e, ArrayBuffer.empty ++= newBytes)
                      }
                      validators += ele
                      validators.foreach {
                        case (s@XMLValidate(_, `node`, f), testData) =>
                          f(new String(testData.toArray)).map(throw _)
                          continueParsing = false
                          completedInstructions += e

                        case x =>
                      }
                    }

                  case e: XMLDelete if e.xPath == node.slice(0, e.xPath.length) =>
                    streamBuffer ++= extractBytes(parsingData, chunkOffset, start)
                    chunkOffset = end
                  case x =>
                })
                advanceParser()

              case XMLStreamConstants.END_ELEMENT =>
                isCharacterBuffering = false
                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => {
                  e match {
                    case e@XMLExtract(`node`, _, false) =>
                      update(xmlElements, node, Some(bufferedText.toString()))

                    case e@XMLExtract(_, _, true) if elementBlockExtracting =>
                      elementBlock.append(extractBytes(parsingData, start, end).utf8String)
                      if (e.xPath == node.toList) {
                        val ele = XMLElement(e.xPath, Map.empty[String, String], Some(elementBlock.toString()))
                        xmlElements.add(ele)
                        elementBlockExtracting = false
                        elementBlock.clear()
                      }
                    case e@XMLInsertAfter(`node`, elementToInsert) =>
                      streamBuffer ++= insertBytes(parsingData, chunkOffset, end, elementToInsert.getBytes)
                      completedInstructions += e
                      chunkOffset = end

                    case e: XMLUpdate if e.xPath.dropRight(1) == node && e.isUpsert =>
                      val input = getUpdatedElement(e.xPath, e.attributes, e.upsertBlock(parser.getPrefix))(parser).getBytes
                      streamBuffer ++= insertBytes(parsingData, chunkOffset, start, input)
                      completedInstructions += e
                      chunkOffset = start

                    case e: XMLUpdate if e.xPath == node.slice(0, e.xPath.length) =>
                      e.xPath match {
                        case path if path == node.toList =>
                          completedInstructions += e
                        case _ =>
                      }
                      chunkOffset = end

                    case e: XMLValidate if e.start == node.slice(0, e.start.length) =>
                      val newBytes = parsingData.slice(start, end)
                      val ele = validators.get(e) match {
                        case Some(x) => (e, x ++= newBytes)
                        case None => throw new IncompleteXMLValidationException
                      }
                      validators += ele

                    case e: XMLDelete if e.xPath == node.slice(0, e.xPath.length) =>
                      chunkOffset = end

                    case x =>
                  }
                })
                bufferedText.clear()
                node -= parser.getLocalName
                advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                instructions.foreach(f = (e: XMLInstruction) => {
                  e match {
                    case e@XMLExtract(`node`, _, false) =>
                      val t = parser.getText()
                      if (t.trim.length > 0) {
                        isCharacterBuffering = true
                        bufferedText.append(t)
                      }

                    case e@XMLExtract(_, _, true) if elementBlockExtracting =>
                      val t = parser.getText()
                      if (t.trim.length > 0) {
                        isCharacterBuffering = true
                        elementBlock.append(t)
                      }

                    case e: XMLUpdate if e.xPath == node.slice(0, e.xPath.length) =>
                      chunkOffset = end

                    case e: XMLValidate if e.start == node.slice(0, e.start.length) =>
                      val newBytes = parsingData.slice(start, end)
                      val ele = validators.get(e) match {
                        case Some(x) => (e, x ++ newBytes)
                        case None => (e, ArrayBuffer.empty ++= newBytes)
                      }
                      validators += ele

                    case e: XMLDelete if e.xPath == node.slice(0, e.xPath.length) =>
                      chunkOffset += (end - start)

                    case _ =>
                  }
                })
                advanceParser()

              case XMLStreamConstants.END_DOCUMENT =>
                for {
                  i <- instructions.diff(completedInstructions).collect { case e: XMLValidate => e }
                } yield {
                  validators.foreach {
                    case (s@XMLValidate(_, _, f), testData) =>
                      f(new String(testData.toArray)).map(throw _)
                      completedInstructions += i
                    case _ =>
                  }
                }
                advanceParser()

              case _ =>
                advanceParser()
            }
          }
        }

        private def getBounds(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): (Int, Int) = {
          val start = reader.getLocationInfo.getStartingByteOffset.toInt
          val end = reader.getLocationInfo.getEndingByteOffset.toInt
          val offset = totalProcessedLength - parsingData.length
          val boundStart = if (start == 1) 0 else start - offset
          val boundEnd = end - offset
          //println(s"getBounds: $start-$end # $boundStart-$boundEnd ${parsingData.slice(boundStart, boundEnd).utf8String}")
          (boundStart, boundEnd)
        }
      }
  }

}
