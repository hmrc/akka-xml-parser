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
      with StreamHelper {
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
            array = grab(in).toArray
            println("grab ---    " + new String(array))
            byteBuffer ++= array
            parser.getInputFeeder.feedInput(array, 0, array.length)
            advanceParser()
          }

          override def onUpstreamFinish(): Unit = {
            parser.getInputFeeder.endOfInput()
            if (!parser.hasNext) {
              push(out, (ByteString(byteBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
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
              push(out, (ByteString(byteBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
              advanceParser()
              byteBuffer.clear()
            }
            else {
              push(out, (ByteString(byteBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
              byteBuffer.clear()
              completeStage()
            }
          }
        })

        var array = Array[Byte]()
        val node = ArrayBuffer[String]()
        val byteBuffer = ArrayBuffer[Byte]()
        val xmlElements = mutable.Set[XMLElement]()
        private var isCharacterBuffering = false
        private val bufferedText = new StringBuilder

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = Try(parser.next())
              .getOrElse {
                XML_ERROR
              }
            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                println("EVENT_INCOMPLETE")
                if (!isClosed(in)) {
                  if (!hasBeenPulled(in)) {
                    println("PULL")
                    pull(in)
                  }
                }
                else failStage(new IllegalStateException("Stream finished before event was fully parsed."))

              case XMLStreamConstants.START_ELEMENT =>
                node += parser.getLocalName
                println("START_ELEMENT - " + node)

                instructions.foreach((e: XMLInstruction) => e match {
                  case e@XMLExtract(`node`, _) => {
                    val keys = getPredicateMatch(parser, e.attributes)
                    xmlElements.add(XMLElement(e.xPath, keys, None))
                  }
                  case _ => {

                  }
                })
                if (parser.hasNext) advanceParser()
                else {
                  completeStage()
                }

              case XMLStreamConstants.END_ELEMENT =>
                isCharacterBuffering = false
                update(xmlElements, node, Some(bufferedText.toString()))
                println("END_ELEMENT - " + bufferedText.toString())
                bufferedText.clear()
                node -= parser.getLocalName
                println("END_ELEMENT" + node)
                if (parser.hasNext) advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                val t = parser.getText()
                println("CHARACTERS - " + t)

                if (t.trim.length > 0) {
                  isCharacterBuffering = true
                  bufferedText.append(t)
                }
                if (parser.hasNext) advanceParser()

              case XML_ERROR =>
                xmlElements.add(XMLElement(Nil, Map.empty, Some(MALFORMED_STATUS)))
                if (isAvailable(out)) {
                  push(out, (ByteString(byteBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
                }
              case x =>
                if (parser.hasNext) advanceParser()
            }
          }
          else {
            completeStage()
          }
        }
      }

  }

}