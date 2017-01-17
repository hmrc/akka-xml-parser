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
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.fasterxml.aalto.stax.InputFactoryImpl
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader, WFCException}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by abhishek on 1/12/16.
  */
object AkkaXMLParser {
  val MALFORMED_STATUS = "Malformed"
  val STREAM_SIZE = "Stream Size"
  val VALIDATION_FAILURE = "Validation failure"
  val XML_START_END_TAGS_MISMATCH = "Start and End tags mismatch. Element(s) - "

  /**
    * Parser Flow that takes a stream of ByteStrings and parses them to (ByteString, Set[XMLElement]).
    */

  def parser(instructions: Set[XMLInstruction]): Flow[ByteString, (ByteString, Set[XMLElement]), NotUsed] =
  Flow.fromGraph(new StreamingXmlParser(instructions))


  private class StreamingXmlParser(instructions: Set[XMLInstruction])
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

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            try {
              chunk = grab(in).toArray
              totalReceivedLength += chunk.length
              parser.getInputFeeder.feedInput(chunk, 0, chunk.length)
              advanceParser()
              push(out, (ByteString(streamBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
              streamBuffer.clear()
              if (incompleteBytes.nonEmpty) {
                streamBuffer ++= incompleteBytes
              }
              chunk = Array.empty[Byte]
            }
            catch {
              case e: WFCException =>
                emitStage(
                  XMLElement(Nil, Map(MALFORMED_STATUS -> e.getMessage), Some(MALFORMED_STATUS)),
                  XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
                )(ByteString(incompleteBytes.toArray ++ chunk))

                completeStage()
              case e: ParserValidationError =>
                emitStage(
                  XMLElement(Nil, Map(VALIDATION_FAILURE -> e.toString), Some(VALIDATION_FAILURE)),
                  XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
                )(ByteString(incompleteBytes.toArray ++ chunk))

                completeStage()
              case e: Throwable =>
                throw e
            }
          }

          override def onUpstreamFinish(): Unit = {
            parser.getInputFeeder.endOfInput()
            try {
              advanceParser()
              if (instructions.count(x => x.isInstanceOf[XMLValidate]) > 0)
                if (completedInstructions.count(x => x.isInstanceOf[XMLValidate])
                  != instructions.count(x => x.isInstanceOf[XMLValidate])) {
                  throw new XMLValidationException
                }

              if (node.nonEmpty)
                xmlElements.add(XMLElement(Nil, Map(MALFORMED_STATUS ->
                  (XML_START_END_TAGS_MISMATCH + node.mkString(", "))), Some(MALFORMED_STATUS)))

              if (streamBuffer.toArray.length > 0) {
                emitStage(
                  XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
                )(ByteString(incompleteBytes.toArray ++ chunk))
                emit(out, (ByteString(streamBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
              }
              else {
                emitStage(
                  XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
                )(ByteString(Array.empty[Byte]))
              }
            }
            catch {
              case e: WFCException =>
                emitStage(
                  XMLElement(Nil, Map(MALFORMED_STATUS -> e.getMessage), Some(MALFORMED_STATUS)),
                  XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
                )(ByteString(incompleteBytes.toArray ++ chunk))
              case e: ParserValidationError =>
                emitStage(
                  XMLElement(Nil, Map(VALIDATION_FAILURE -> e.toString), Some(VALIDATION_FAILURE)),
                  XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
                )(ByteString(incompleteBytes.toArray ++ chunk))
              case e: Throwable =>
                throw e
            }
            completeStage()
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        var chunk = Array[Byte]()
        var totalReceivedLength = 0
        var incompleteBytesLength = 0
        var isCharacterBuffering = false


        val node = ArrayBuffer[String]()
        val nodesToProcess = ArrayBuffer[String]()
        val streamBuffer = ArrayBuffer[Byte]()
        val incompleteBytes = ArrayBuffer[Byte]()
        val completedInstructions = mutable.Set[XMLInstruction]()
        val xmlElements = mutable.Set[XMLElement]()
        val validators = mutable.Map[XMLValidate, ArrayBuffer[Byte]]()
        val bufferedText = new StringBuilder


        private def emitStage(elementsToAdd: XMLElement*)(bytesToEmit: ByteString) = {
          elementsToAdd.foreach(xmlElements.add)
          emit(out, (bytesToEmit, getCompletedXMLElements(xmlElements).toSet))
        }

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            val (start, end) = getBounds(parser)
            val isEmptyElement = parser.isEmptyElement
            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                val lastChunkOffset = totalReceivedLength - chunk.length
                if (chunk.length > 0) {
                  val lastCompleteElementInChunk = chunk.slice(0,
                    start - lastChunkOffset)
                  incompleteBytes ++= chunk.slice(lastCompleteElementInChunk.length, chunk.length)
                  if (lastCompleteElementInChunk.length == 0 && (end - start) > chunk.length) streamBuffer.clear()
                  else streamBuffer ++= lastCompleteElementInChunk
                  incompleteBytesLength = incompleteBytes.length
                }

              case XMLStreamConstants.START_ELEMENT =>
                node += parser.getLocalName
                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => e match {
                  case e@XMLExtract(`node`, _) if getPredicateMatch(parser, e.attributes).nonEmpty || e.attributes.isEmpty =>
                    val keys = getPredicateMatch(parser, e.attributes)
                    val ele = XMLElement(e.xPath, keys, None)
                    xmlElements.add(ele)
                    nodesToProcess += parser.getLocalName

                  case e@XMLUpdate(`node`, value, attributes, _) =>
                    //TODO : Refactor the below code
                    val lastChunkOffset = totalReceivedLength - chunk.length
                    val input = getUpdatedElement(e.xPath, e.attributes, e.value, isEmptyElement)(parser).getBytes
                    val newBytes = getHeadAndTail(chunk, start - lastChunkOffset,
                      end - lastChunkOffset, input, incompleteBytesLength)
                    streamBuffer ++= newBytes._1
                    chunk = newBytes._2
                    completedInstructions += e
                    nodesToProcess += parser.getLocalName

                  case e: XMLUpdate if e.xPath.dropRight(1) == node && e.isUpsert =>
                    nodesToProcess += parser.getLocalName


                  case e: XMLValidate if e.start == node.slice(0, e.start.length) =>
                    val lastChunkOffset = totalReceivedLength - chunk.length
                    val newBytes = (streamBuffer.toArray ++ chunk).slice(start -
                      lastChunkOffset + incompleteBytesLength,
                      end - lastChunkOffset + incompleteBytesLength)
                    if (!isEmptyElement) {
                      val ele = validators.get(e) match {
                        case Some(x) => (e, x ++ newBytes)
                        case None => (e, ArrayBuffer.empty ++= newBytes)
                      }
                      validators += ele
                    }
                    nodesToProcess += parser.getLocalName

                  case x =>
                })
                incompleteBytes.clear()
                if (parser.hasNext) advanceParser()

              case XMLStreamConstants.END_ELEMENT =>
                isCharacterBuffering = false
                if (!nodesToProcess.isEmpty)
                  instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => {
                    e match {
                      case e@XMLExtract(`node`, _) =>
                        update(xmlElements, node, Some(bufferedText.toString()))
                        nodesToProcess -= parser.getLocalName

                      case e: XMLUpdate if e.xPath.dropRight(1) == node && e.isUpsert =>
                        val lastChunkOffset = totalReceivedLength - chunk.length
                        val input = getUpdatedElement(e.xPath, e.attributes, e.value, isEmptyElement = true)(parser).getBytes

                        val newBytes = insertBytesInChunk(chunk, start - lastChunkOffset, input)
                        chunk = chunk.slice(start - lastChunkOffset, chunk.length)
                        streamBuffer ++= newBytes
                        completedInstructions += e
                        nodesToProcess -= parser.getLocalName

                      case e: XMLValidate if e.start == node.slice(0, e.start.length) =>
                        val lastChunkOffset = totalReceivedLength - chunk.length
                        val newBytes = (streamBuffer.toArray ++ chunk).slice(start - lastChunkOffset
                          + incompleteBytesLength,
                          end - lastChunkOffset + incompleteBytesLength)

                        val ele = validators.get(e) match {
                          case Some(x) => (e, x ++= newBytes)
                          case None => {
                            throw new XMLValidationException
                          }
                        }
                        validators += ele
                        validators.foreach {
                          case (s@XMLValidate(_, `node`, f), testData) =>
                            f(new String(testData.toArray)).map(throw _)
                            completedInstructions += e
                          case x =>
                        }
                        nodesToProcess -= parser.getLocalName
                      case x =>
                    }
                  })
                incompleteBytes.clear()
                bufferedText.clear()
                node -= parser.getLocalName
                if (parser.hasNext) advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                if (!nodesToProcess.isEmpty)
                  instructions.foreach(f = (e: XMLInstruction) => {
                    e match {
                      case e@XMLExtract(`node`, _) =>
                        val t = parser.getText()
                        if (t.trim.length > 0) {
                          isCharacterBuffering = true
                          bufferedText.append(t)
                        }
                      case e@XMLUpdate(`node`, _, _, _) =>
                        chunk = chunk.slice(end - start, chunk.length)

                      case e: XMLValidate if e.start == node.slice(0, e.start.length) =>
                        val lastChunkOffset = totalReceivedLength - chunk.length
                        val newBytes = (streamBuffer.toArray ++ chunk).slice(start - lastChunkOffset + incompleteBytesLength,
                          end - lastChunkOffset + incompleteBytesLength)
                        val ele = validators.get(e) match {
                          case Some(x) => (e, x ++ newBytes)
                          case None => (e, ArrayBuffer.empty ++= newBytes)
                        }
                        validators += ele
                      case _ =>
                    }
                  })
                if (parser.hasNext) advanceParser()

              case x =>
                if (parser.hasNext) advanceParser()
            }
          }
        }
      }

    private def getBounds(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): (Int, Int) = {
      val start = reader.getLocationInfo.getStartingByteOffset.toInt
      (
        if (start == 1) 0 else start,
        reader.getLocationInfo.getEndingByteOffset.toInt
        )
    }
  }

}
