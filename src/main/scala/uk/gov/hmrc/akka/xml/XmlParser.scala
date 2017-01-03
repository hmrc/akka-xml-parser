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
            totalReceivedLength += chunk.length

            if (stopParsing) {
              push(out, (ByteString(incompleteBytes.toArray ++ chunk), getCompletedXMLElements(xmlElements).toSet))
              byteBuffer.clear()
              incompleteBytes.clear()
            } else {
              parser.getInputFeeder.feedInput(chunk, 0, chunk.length)
              advanceParser()
              push(out, (ByteString(byteBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
              byteBuffer.clear()
              if (incompleteBytes.length > 0) {
                byteBuffer ++= incompleteBytes
                incompleteBytes.clear()
              }
            }
          }

          override def onUpstreamFinish(): Unit = {
            if (!stopParsing) {
              parser.getInputFeeder.endOfInput()
              advanceParser()
            }

            if (instructions.count(x => x.isInstanceOf[XMLValidate]) > 0)
              if (completedInstructions.count(x => x.isInstanceOf[XMLValidate])
                != instructions.count(x => x.isInstanceOf[XMLValidate])) {
                throw new XMLValidationException
              }
            if (node.length > 0)
              xmlElements.add(XMLElement(Nil, Map.empty, Some(MALFORMED_STATUS)))

            if (stopParsing) {
              emit(out, (ByteString(incompleteBytes.toArray ++ chunk), getCompletedXMLElements(xmlElements).toSet))
              byteBuffer.clear()
              incompleteBytes.clear()
            } else if (byteBuffer.toArray.length > 0) {
              emit(out, (ByteString(byteBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
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
        var stopParsing = false
        val node = ArrayBuffer[String]()
        val byteBuffer = ArrayBuffer[Byte]()
        val incompleteBytes = ArrayBuffer[Byte]()
        val completedInstructions = mutable.Set[XMLInstruction]()
        val xmlElements = mutable.Set[XMLElement]()
        val validators = mutable.Map[XMLValidate, ArrayBuffer[Byte]]()

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
                if (chunk.length > 0) {
                  val lastCompleteElementInChunk = chunk.slice(0,
                    (start - ((totalReceivedLength - chunk.length))))
                  incompleteBytes ++= chunk.slice(lastCompleteElementInChunk.length, chunk.length)
                  byteBuffer ++= lastCompleteElementInChunk
                  incompleteBytesLength = incompleteBytes.length
                }
              //else failStage(new IllegalStateException("Stream finished before event was fully parsed."))

              case XMLStreamConstants.START_ELEMENT =>
                node += parser.getLocalName
                instructions.filter(x => !completedInstructions.contains(x)).foreach((e: XMLInstruction) => e match {
                  case e@XMLExtract(`node`, _) if getPredicateMatch(parser, e.attributes).nonEmpty || e.attributes.isEmpty => {
                    val keys = getPredicateMatch(parser, e.attributes)
                    val ele = XMLElement(e.xPath, keys, None)
                    xmlElements.add(ele)
                  }
                  case e@XMLUpdate(`node`, value, attributes, _) => {
                    //TODO : Refactor the below code
                    val input = getUpdatedElement(e.xPath, e.attributes, e.value, isEmptyElement)(parser).getBytes
                    val newBytes = getHeadAndTail(chunk, (start - (totalReceivedLength - chunk.length)),
                      (end - (totalReceivedLength - chunk.length)), input, incompleteBytesLength)
                    byteBuffer ++= newBytes._1
                    chunk = newBytes._2
                    completedInstructions += e
                  }
                  case e: XMLValidate if e.start == node.slice(0, e.start.length) => {
                    val newBytes = (byteBuffer.toArray ++ chunk).slice(start -
                      (totalReceivedLength - chunk.length) + incompleteBytesLength,
                      end - (totalReceivedLength - chunk.length) + incompleteBytesLength)
                    if (!isEmptyElement) {
                      val ele = validators.get(e) match {
                        case Some(x) => (e, x ++ newBytes)
                        case None => (e, ArrayBuffer.empty ++= newBytes)
                      }
                      validators += (ele)
                    }
                  }
                  case x => {
                  }
                })
                if (parser.hasNext) advanceParser()

              case XMLStreamConstants.END_ELEMENT =>
                isCharacterBuffering = false
                instructions.filter(x => !completedInstructions.contains(x)).foreach(f = (e: XMLInstruction) => {
                  e match {
                    case e@XMLExtract(`node`, _) => {
                      update(xmlElements, node, Some(bufferedText.toString()))
                    }
                    case e: XMLUpdate if e.xPath.dropRight(1) == node && e.isUpsert => {
                      val input = getUpdatedElement(e.xPath, e.attributes, e.value, true)(parser).getBytes
                      val newBytes = insertBytes(chunk, (start - (totalReceivedLength - chunk.length)), input)
                      chunk = chunk.slice(start - (totalReceivedLength - chunk.length), chunk.length)
                      byteBuffer ++= newBytes
                      completedInstructions += e
                    }

                    case e: XMLValidate if e.start == node.slice(0, e.start.length) => {
                      val newBytes = (byteBuffer.toArray ++ chunk).slice(start - (totalReceivedLength - chunk.length) + incompleteBytesLength,
                        end - (totalReceivedLength - chunk.length) + incompleteBytesLength)
                      val ele = validators.get(e) match {
                        case Some(x) => (e, x ++= newBytes)
                        case None => throw new XMLValidationException
                      }
                      validators += (ele)
                      validators.foreach(t => {
                        t match {
                          case (s@XMLValidate(_, `node`, f), testData) =>
                            f(new String(testData.toArray)).map(throw _)
                            completedInstructions += e
                          case x => {
                          }
                        }
                      })
                    }
                    case x => {
                    }
                  }
                })
                bufferedText.clear()
                node -= parser.getLocalName
                if (parser.hasNext) advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                instructions.foreach((e: XMLInstruction) => {
                  e match {
                    case e@XMLExtract(`node`, _) => {
                      val t = parser.getText()
                      if (t.trim.length > 0) {
                        isCharacterBuffering = true
                        bufferedText.append(t)
                      }
                    }
                    case e@XMLUpdate(`node`, _, _, _) => {
                      chunk = getTailBytes(chunk, end - start)
                    }
                    case e: XMLValidate if e.start == node.slice(0, e.start.length) => {
                      val newBytes = (byteBuffer.toArray ++ chunk).slice(start - (totalReceivedLength - chunk.length) + incompleteBytesLength,
                        end - (totalReceivedLength - chunk.length) + incompleteBytesLength)
                      val ele = validators.get(e) match {
                        case Some(x) => (e, x ++ newBytes)
                        case None => (e, ArrayBuffer.empty ++= newBytes)
                      }
                      validators += (ele)
                    }
                    case _ => {
                    }
                  }
                })
                if (parser.hasNext) advanceParser()

              case XML_ERROR =>
                xmlElements.add(XMLElement(Nil, Map.empty, Some(MALFORMED_STATUS)))
                stopParsing = true
              //if (parser.hasNext) advanceParser()

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
