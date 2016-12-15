/*
 * Copyright 2016 HM Revenue & Customs
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
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
  * Created by abhishek on 1/12/16.
  */
object AkkaXMLParser {
  val XML_ERROR = 258
  val MALFORMED_STATUS = "Malformed"

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
            chunk = grab(in).toArray
            parser.getInputFeeder.feedInput(chunk, 0, chunk.length)
            advanceParser()
          }

          override def onUpstreamFinish(): Unit = {
            parser.getInputFeeder.endOfInput()
            if (!parser.hasNext) {
              push(out, (ByteString(chunk), getCompletedXMLElements(xmlElements).toSet))
              completeStage()
            }
            else if (isAvailable(out)) {
              advanceParser()
            }
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            if (!isClosed(in)) {
              push(out, (ByteString(bytesToPushBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
              advanceParser()
              bytesToPushBuffer.clear()
              if (offsetByteBuffer.length > 0) {
                bytesToPushBuffer ++= offsetByteBuffer
                offsetByteBuffer.clear()
              }
            }
            else {
              push(out, (ByteString(bytesToPushBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
              bytesToPushBuffer.clear()
              if (offsetByteBuffer.length > 0) {
                bytesToPushBuffer ++= offsetByteBuffer
                offsetByteBuffer.clear()
              }
              completeStage()
            }
          }
        })

        var chunk = Array[Byte]()
        var pointer = 0
        var offset = 0

        val node = ArrayBuffer[String]()
        val bytesToPushBuffer = ArrayBuffer[Byte]()
        val offsetByteBuffer = ArrayBuffer[Byte]()
        val xmlElements = mutable.Set[XMLElement]()

        private var isCharacterBuffering = false
        private val bufferedText = new StringBuilder

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = Try(parser.next())
              .getOrElse {
                XML_ERROR
              }
            val (start, end) = getBounds(parser)
            val isEmptyElement = parser.isEmptyElement

            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                if (!isClosed(in)) {
                  if (!hasBeenPulled(in)) {
                    //TODO : Refactor this code
                    if (chunk.length > 0) {
                      val lastCompleteElementInChunk = chunk.slice(0, (start - (pointer + offset)))
                      bytesToPushBuffer ++= lastCompleteElementInChunk
                      pointer = start
                      offsetByteBuffer ++= chunk.slice(lastCompleteElementInChunk.length, chunk.length)
                      offset = offsetByteBuffer.length
                    }
                    pull(in)
                  }
                }
                else failStage(new IllegalStateException("Stream finished before event was fully parsed."))

              case XMLStreamConstants.START_ELEMENT =>
                node += parser.getLocalName
                instructions.foreach((e: XMLInstruction) => e match {
                  case e@XMLExtract(`node`, _) if getPredicateMatch(parser, e.attributes).nonEmpty || e.attributes.isEmpty => {
                    val keys = getPredicateMatch(parser, e.attributes)
                    val ele = XMLElement(e.xPath, keys, None)
                    xmlElements.add(ele)
                  }
                  case e@XMLUpdate(`node`, value, attributes, _) => {
                    //TODO : Refactor the below code
                    val input = getUpdatedElement(e.xPath, e.attributes, e.value, isEmptyElement)(parser).getBytes
                    val newBytes = getHeadAndTail(chunk, start - pointer, end - pointer, input, offset)
                    bytesToPushBuffer ++= newBytes._1
                    chunk = newBytes._2
                    pointer = end
                  }
                  case x => {
                  }
                })
                if (parser.hasNext) advanceParser()

              case XMLStreamConstants.END_ELEMENT =>
                //TODO : Optimize this step so that we buffer data only for elements that we need
                isCharacterBuffering = false
                instructions.foreach((e: XMLInstruction) => {
                  e match {
                    case e@XMLExtract(`node`, _) => {
                      update(xmlElements, node, Some(bufferedText.toString()))
                    }
                    case e: XMLUpdate if e.xPath.dropRight(1) == node && e.isUpsert => {
                      val input = getUpdatedElement(e.xPath, e.attributes, e.value, true)(parser).getBytes
                      val newBytes = insertBytes(chunk, start - (pointer + offset), input)
                      chunk = chunk.slice(start - (pointer + offset), chunk.length)
                      bytesToPushBuffer ++= newBytes
                      offset = 0
                      pointer = end
                    }
                    case x => {
                    }
                  }
                })
                bufferedText.clear()
                node -= parser.getLocalName
                if (parser.hasNext) advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                //TODO : Optimize this step so that we buffer data only for elements that we need
                instructions.foreach((e: XMLInstruction) => {
                  e match {
                    case e@XMLExtract(`node`, _) => {
                      val t = parser.getText()
                      isCharacterBuffering = true
                      bufferedText.append(t)
                    }
                    case e@XMLUpdate(`node`, _, _, _) => {
                      pointer = end
                      chunk = getTailBytes(chunk, end - start)
                    }
                    case _ => {

                    }
                  }

                })
                if (parser.hasNext) advanceParser()

              case XML_ERROR =>
                xmlElements.add(XMLElement(Nil, Map.empty, Some(MALFORMED_STATUS)))
                bytesToPushBuffer ++= chunk
                if (isAvailable(out)) {
                  push(out, (ByteString(bytesToPushBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
                }
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