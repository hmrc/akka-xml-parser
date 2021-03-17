/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.xml.stream.XMLStreamConstants

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.fasterxml.aalto.stax.InputFactoryImpl
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by abhishek on 23/03/17.
  */
object TransformStage {
  def parser(instructions: Set[XMLInstruction])
  : Flow[ParsingData, ByteString, NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(instructions))
  }

  private class StreamingXmlParser(instructions: Set[XMLInstruction])
    extends GraphStage[FlowShape[ParsingData, ByteString]]
      with StreamHelper
      with ParsingDataFunctions {
    val in: Inlet[ParsingData] = Inlet("Transform.in")
    val out: Outlet[ByteString] = Outlet("Transform.out")

    override val shape: FlowShape[ParsingData, ByteString] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        private val buffer = ByteString.empty

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            parsingData = grab(in)
            chunkOffset = 0

            parser.getInputFeeder.feedInput(parsingData.data.toArray, 0, parsingData.data.length)
            advanceParser()
            emitChunk()
          }

          override def onUpstreamFinish(): Unit = {
            emit(out, buffer)
            completeStage()
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        private def emitChunk(): Unit = {
          if (hasTransformHappened) {
            push(out, (ByteString(streamBuffer.toArray)))
            streamBuffer.clear()
          }
          else
            push(out, (ByteString("")))
        }

        val node = ArrayBuffer[String]()
        val streamBuffer = ArrayBuffer[Byte]()
        val completedInstructions = mutable.Set[XMLInstruction]()


        var parsingData = ParsingData(ByteString(""), Set.empty, 0)
        var hasTransformHappened = false
        var chunkOffset = 0


        private val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
        private val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = feeder.createAsyncFor(Array.empty)

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            val (elementStart, elementEnd) = getBounds(parser)
            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                if (hasTransformHappened)
                  streamBuffer ++= parsingData.data.slice(chunkOffset, parsingData.data.length)
                chunkOffset = elementEnd

              case XMLStreamConstants.START_ELEMENT =>
                node += parser.getLocalName
                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => e match {
                  case e: XMLTransform if e.startPath == node.slice(0, e.startPath.length) =>
                    if (!parser.isEmptyElement) {
                      val newBytes = parsingData.data.slice(elementStart, elementEnd)
                      streamBuffer ++= newBytes
                    }

                  case e: XMLRemoveNamespacePrefix if (node == e.xPath && e.removeForStartTag) => {
                    val _ = Option(parser.getPrefix) match {
                      case Some(pre) if pre.length > 0 =>
                        streamBuffer ++= extractBytes(parsingData.data, chunkOffset, elementStart)
                        streamBuffer ++= ByteString("<")
                        streamBuffer ++= parsingData.data.slice(elementStart + 2 + pre.length, elementEnd)
                        chunkOffset = elementEnd
                      case _ =>
                    }
                  }

                  case x =>
                })
                advanceParser()

              case XMLStreamConstants.END_ELEMENT =>
                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => {
                  e match {
                    case e: XMLTransform if e.startPath == node.slice(0, e.startPath.length) =>
                      val newBytes = parsingData.data.slice(elementStart, elementEnd)
                      streamBuffer ++= newBytes
                      if (node == e.endPath) {
                        val transformedData: String = e.f(new String(streamBuffer.toArray))
                        hasTransformHappened = true
                        streamBuffer.clear()
                        streamBuffer ++= ByteString(transformedData)
                        chunkOffset = elementEnd
                        completedInstructions += e
                      }

                    case e: XMLRemoveNamespacePrefix if (node == e.xPath && e.removeForEndTag) => {
                      val _ = Option(parser.getPrefix) match {
                        case Some(pre) if pre.length > 0 =>
                          streamBuffer ++= extractBytes(parsingData.data, chunkOffset, elementStart)
                          streamBuffer ++= ByteString("</")
                          streamBuffer ++= parsingData.data.slice(elementStart + 3 + pre.length, elementEnd)
                          chunkOffset = elementEnd

                        case _ =>
                      }
                    }

                    case x =>
                  }
                })
                node -= parser.getLocalName
                advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => {
                  e match {
                    case e: XMLTransform if e.startPath == node.slice(0, e.startPath.length) =>
                      val newBytes = parsingData.data.slice(elementStart, elementEnd)
                      streamBuffer ++= newBytes

                    case e: XMLRemoveNamespacePrefix if (node == e.xPath) =>
                      val newBytes = parsingData.data.slice(elementStart, elementEnd)
                      streamBuffer ++= newBytes

                    case _ =>
                  }
                })
                advanceParser()

              case _ =>
                advanceParser()
            }
          }
        }

        private def getBounds(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): (Int, Int) = {
          val staxStart = reader.getLocationInfo.getStartingByteOffset.toInt
          (
            if (staxStart == 1) 0 else staxStart - (parsingData.totalProcessedLength - parsingData.data.length),
            reader.getLocationInfo.getEndingByteOffset.toInt - (parsingData.totalProcessedLength - parsingData.data.length)
            )
        }

      }
  }

}
