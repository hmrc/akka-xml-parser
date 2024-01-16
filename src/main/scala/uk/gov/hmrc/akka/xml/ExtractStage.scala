/*
 * Copyright 2022 HM Revenue & Customs
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

import com.fasterxml.aalto.stax.InputFactoryImpl
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader, WFCException}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.pekko.stream.{Attributes, FlowShape, Inlet, Outlet}
import org.apache.pekko.util.ByteString

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object ExtractStage {
  val MALFORMED_STATUS = "Malformed"
  val XML_START_END_TAGS_MISMATCH = "Start and End tags mismatch. Element(s) - "

  def parser(instructions: Seq[XMLInstruction],
             parentNodes: Option[Seq[String]] = None): Flow[ByteString, (ByteString, Set[XMLGroupElement]), NotUsed] =
    Flow.fromGraph(new StreamingXmlParser(instructions, parentNodes))

  private class StreamingXmlParser(instructions: Seq[XMLInstruction], parentNodes: Option[Seq[String]])
    extends GraphStage[FlowShape[ByteString, (ByteString, Set[XMLGroupElement])]] with ExtractStageHelpers {
    val in: Inlet[ByteString] = Inlet("XMLParser.in")
    val out: Outlet[(ByteString, Set[XMLGroupElement])] = Outlet("XMLParser.out")
    override val shape: FlowShape[ByteString, (ByteString, Set[XMLGroupElement])] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        import javax.xml.stream.XMLStreamConstants

        private val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
        private val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = feeder.createAsyncFor(Array.empty)

        setHandler(in, new InHandler {
          override def onPush(): Unit = processStage(processOnPush)

          override def onUpstreamFinish(): Unit = {
            parser.getInputFeeder.endOfInput()
            processStage(processOnUpstreamFinish)
            completeStage()
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = pull(in)
        })

        private def processOnPush() = {
          chunk = grab(in)
          Try {
            parser.getInputFeeder.feedInput(chunk.toArray, 0, chunk.length)
            advanceParser()
            push(out, (chunk,
              getCompletedXMLElements(xmlElements).toSet))
            // the last chunk has already been sent, so need to reset before the emit stage to avoid duplication
            chunk = ByteString("")
          }
        }

        private def processOnUpstreamFinish() = {
          for {
            _ <- Try(advanceParser())
          } yield {
            if (node.nonEmpty)
              xmlElements.add(XMLGroupElement(Nil, Map(MALFORMED_STATUS ->
                (XML_START_END_TAGS_MISMATCH + node.mkString(", "))), Some(MALFORMED_STATUS)))
            emitStage()
          }
        }

        private def processStage(f: () => Try[Unit]) = {
          f().recover {
            case e: WFCException =>
              emitStage(
                XMLGroupElement(Nil, Map(MALFORMED_STATUS -> e.getMessage), Some(MALFORMED_STATUS)))
              completeStage()
            case e: Throwable =>
              throw e
          }
        }

        private def emitStage(elementsToAdd: XMLGroupElement*) = {
          emit(out, (chunk, getCompletedXMLElements(xmlElements).toSet ++ elementsToAdd))
        }

        private var chunk = ByteString("")

        private val node = ArrayBuffer[String]()
        private val xmlElements = mutable.Set[XMLGroupElement]()
        private val bufferedText = new StringBuilder
        private val groupings = parentNodes.map(nodes =>
          mutable.Map.apply(nodes.map((_, (0, false))): _*))

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>

              case XMLStreamConstants.START_ELEMENT =>
                val localName = parser.getLocalName
                val activeGroupings = groupings collect {
                  case nodes =>
                    if (nodes.contains(localName)) {
                      val currentSeq = nodes(localName)._1
                      nodes(localName) = (currentSeq + 1, true)
                    }
                    nodes.filter(_._2._2 == true)
                      .view.mapValues(_._1)
                }
                node += localName
                instructions.foreach(f = (e: XMLInstruction) => e match {
                  case e@XMLExtract(`node`, _, false) if ExtractNameSpace(parser, e.attributes).nonEmpty || e.attributes.isEmpty =>
                    val keys = ExtractNameSpace(parser, e.attributes)
                    val groupedNodes = activeGroupings collect {
                      case nodes if nodes.nonEmpty => nodes.map(group => XMLGroup(group._1, group._2)).toSeq
                    }
                    val ele = XMLGroupElement(e.xPath, keys, None, groupedNodes)
                    xmlElements.add(ele)

                  case _ =>
                })
                advanceParser()

              case XMLStreamConstants.END_ELEMENT =>
                val localName = parser.getLocalName

                groupings collect {
                  case nodes if nodes.contains(localName) =>
                    val currentSeq = nodes(localName)._1
                    nodes(localName) = (currentSeq, false)
                }

                instructions.foreach(f = (e: XMLInstruction) => {
                  e match {
                    case XMLExtract(`node`, _, false) =>
                      update(xmlElements, node, Some(bufferedText.toString()))

                    case _ =>
                  }
                })
                bufferedText.clear()
                node -= localName
                advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                instructions.foreach(f = (e: XMLInstruction) => {
                  e match {
                    case XMLExtract(`node`, _, false) =>
                      val t = parser.getText()
                      if (t.trim.length > 0) {
                        bufferedText.append(t)
                      }

                    case _ =>
                  }
                })
                advanceParser()

              case _ => advanceParser()
            }
          }
        }
      }
  }

}
