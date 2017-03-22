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

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

object EMACParsingStage {
  val MALFORMED_STATUS = "Malformed"
  val STREAM_MAX_SIZE = "Stream max size"
  val STREAM_IS_EMPTY = "Stream is empty"
  val STREAM_SIZE_LESS_AND_BELOW_MIN = "Stream Size"
  val STREAM_SIZE = "Stream Size"
  val NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE = "No validation tags were found in first n bytes failure"
  val VALIDATION_INSTRUCTION_FAILURE = "Validation instruction failure"
  val PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE = "Not all of the xml validations / checks were done"
  val XML_START_END_TAGS_MISMATCH = "Start and End tags mismatch. Element(s) - "

  def parser(instructions: Set[XMLInstruction], validationMaxSize: Option[Int] = None, validationMaxSizeOffset: Int = 10000, parentNodes: Option[Seq[String]] = None)
  : Flow[ParsingData, (ByteString, Set[XMLGroupElement]), NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(instructions, validationMaxSize, validationMaxSizeOffset, parentNodes))
  }

  private class StreamingXmlParser(instructions: Set[XMLInstruction], validationMaxSize: Option[Int] = None, validationMaxSizeOffset: Int, parentNodes: Option[Seq[String]])
    extends GraphStage[FlowShape[ParsingData, (ByteString, Set[XMLGroupElement])]]
      with EMACStreamHelper
      with ParsingDataFunctions {
    val in: Inlet[ParsingData] = Inlet("XMLParser.in")
    val out: Outlet[(ByteString, Set[XMLGroupElement])] = Outlet("XMLParser.out")
    override val shape: FlowShape[ParsingData, (ByteString, Set[XMLGroupElement])] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        import javax.xml.stream.XMLStreamConstants

        private val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
        private val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = feeder.createAsyncFor(Array.empty)

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

        def processOnPush() = {
          parsingData = grab(in)
          chunkOffset = 0
          (validationMaxSize) match {
            case Some(validationSize)
              if parsingData.totalProcessedLength > (validationSize + validationMaxSizeOffset) &&
                instructions.collect { case e: XMLValidate => e }.exists(!completedInstructions.contains(_)) =>
              Failure(new NoValidationTagsFoundWithinFirstNBytesException)
            case _ =>
              Try {
                parser.getInputFeeder.feedInput(parsingData.data.toArray, 0, parsingData.data.length)
                advanceParser()
                push(out, (ByteString(streamBuffer.toArray),
                  getCompletedXMLElements(xmlElements).toSet))
                streamBuffer.clear()
              }
          }
        }

        def processOnUpstreamFinish(): Try[Unit] = {
          for {
            _ <- Try(advanceParser())
            _ <- if ((instructions.count(x => x.isInstanceOf[XMLValidate]) > 0) &&
              (completedInstructions.count(x => x.isInstanceOf[XMLValidate]) != instructions.count(x => x.isInstanceOf[XMLValidate]))) {
              Failure(new IncompleteXMLValidationException)
            }
            else Success()
          } yield {
            if (node.nonEmpty)
              xmlElements.add(XMLGroupElement(Nil, Map(MALFORMED_STATUS ->
                (XML_START_END_TAGS_MISMATCH + node.mkString(", "))), Some(MALFORMED_STATUS)))
            emitStage()
          }
        }

        def processStage(f: () => Try[Unit]) = {
          f().recover {
            case e: WFCException =>
              emitStage(
                XMLGroupElement(Nil, Map(MALFORMED_STATUS -> e.getMessage), Some(MALFORMED_STATUS)))
              completeStage()
            case e: NoValidationTagsFoundWithinFirstNBytesException =>
              emitStage(
                XMLGroupElement(Nil, Map(NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE -> ""),
                  Some(NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE)),
                XMLGroupElement(Nil, Map(STREAM_SIZE -> parsingData.totalProcessedLength.toString), Some(STREAM_SIZE)))
              completeStage()
            case e: IncompleteXMLValidationException =>
              emitStage(
                XMLGroupElement(Nil, Map(PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE -> ""),
                  Some(PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE)))
              completeStage()
            case e: ParserValidationError =>
              emitStage(
                XMLGroupElement(Nil, Map(VALIDATION_INSTRUCTION_FAILURE -> e.toString), Some(VALIDATION_INSTRUCTION_FAILURE))
              )
              completeStage()
            case e: Throwable =>
              throw e
          }
        }

        private def emitStage(elementsToAdd: XMLGroupElement*) = {
          emit(out, (parsingData.data, getCompletedXMLElements(xmlElements).toSet ++ elementsToAdd ++ parsingData.extractedElements
            .map(ele => XMLGroupElement(ele.xPath, ele.attributes, ele.value, None))))
        }

        var parsingData = ParsingData(ByteString(""), Set.empty, 0)
        var isCharacterBuffering = false
        var chunkOffset = 0

        val node = ArrayBuffer[String]()
        val completedInstructions = mutable.Set[XMLInstruction]()
        val xmlElements = mutable.Set[XMLGroupElement]()
        val bufferedText = new StringBuilder
        val streamBuffer = ArrayBuffer[Byte]()
        val validators = mutable.Map[XMLValidate, ArrayBuffer[Byte]]()
        val groupings = parentNodes.map(nodes =>
          mutable.Map.apply(nodes.map((_, (0, false))): _*))

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            val (start, end) = getBounds(parser)
            event match {

              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                streamBuffer ++= parsingData.data.slice(chunkOffset, parsingData.data.length)

              case XMLStreamConstants.START_ELEMENT =>
                val localName = parser.getLocalName
                val activeGroupings = groupings collect {
                  case nodes =>
                    if (nodes.contains(localName)) {
                      val currentSeq = nodes(localName)._1
                      nodes(localName) = (currentSeq + 1, true)
                    }
                    nodes.filter(_._2._2 == true)
                      .mapValues(_._1)
                }
                node += localName
                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => e match {
                  case e@XMLExtract(`node`, _) if getPredicateMatch(parser, e.attributes).nonEmpty || e.attributes.isEmpty =>
                    val keys = getPredicateMatch(parser, e.attributes)
                    val groupedNodes = activeGroupings collect {
                      case nodes if nodes.nonEmpty => nodes.map(group => XMLGroup(group._1, group._2)).toSeq
                    }
                    val ele = XMLGroupElement(e.xPath, keys, None, groupedNodes)
                    xmlElements.add(ele)

                  case x =>
                })
                if (parser.hasNext) advanceParser()

              case XMLStreamConstants.END_ELEMENT =>
                val localName = parser.getLocalName
                isCharacterBuffering = false

                groupings collect {
                  case nodes if nodes.contains(localName) =>
                    val currentSeq = nodes(localName)._1
                    nodes(localName) = (currentSeq, false)
                }

                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => {
                  e match {
                    case e@XMLExtract(`node`, _) =>
                      update(xmlElements, node, Some(bufferedText.toString()))

                    case x =>
                  }
                })
                bufferedText.clear()
                node -= localName
                if (parser.hasNext) advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                instructions.foreach(f = (e: XMLInstruction) => {
                  e match {
                    case e@XMLExtract(`node`, _) =>
                      val t = parser.getText()
                      if (t.trim.length > 0) {
                        isCharacterBuffering = true
                        bufferedText.append(t)
                      }

                    case _ =>
                  }
                })
                if (parser.hasNext) advanceParser()

              case x =>
                if (parser.hasNext) advanceParser()
            }
          }
        }

        private def getBounds(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): (Int, Int) = {
          val start = reader.getLocationInfo.getStartingByteOffset.toInt
          (
            if (start == 1) 0 else start - (parsingData.totalProcessedLength - parsingData.data.length),
            reader.getLocationInfo.getEndingByteOffset.toInt - (parsingData.totalProcessedLength - parsingData.data.length)
          )
        }
      }
  }

}
