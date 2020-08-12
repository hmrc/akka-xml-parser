/*
 * Copyright 2020 HM Revenue & Customs
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
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.fasterxml.aalto.in.CharSourceBootstrapper
import com.fasterxml.aalto.stax.InputFactoryImpl
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader, WFCException}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Try}

/**
  * This Stage will only let whole xml tags through or partial or whole text nodes.
  * For example if the incoming xml is:  <aa><bb><c then the output will be <aa><bb> and <c will be cached waiting for the next incoming data
  * Say next time the incoming xml part is: c>ABCD  then the output will be <cc>ABCD  You can see how the xml tag is whole but text is cut in half
  * The purpose of doing this is that in a downstream stage we can reliably parse xml tags, without worrying about half finished tags.
  * Created by abhishek on 28/02/17.
  */
@deprecated("Use FastParsingStage instead","akka-xml-parser 1.0.0")
object CompleteChunkStage {
  val MALFORMED_STATUS = "Malformed"
  val STREAM_MAX_SIZE = "Stream max size"
  val STREAM_IS_EMPTY = "Stream is empty"
  val STREAM_SIZE_LESS_AND_BELOW_MIN = "Stream Size"
  val STREAM_SIZE = "Stream Size"
  val NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE = "No validation tags were found in first n bytes failure"
  val VALIDATION_INSTRUCTION_FAILURE = "Validation instruction failure"
  val PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE = "Not all of the xml validations / checks were done"
  val XML_START_END_TAGS_MISMATCH = "Start and End tags mismatch. Element(s) - "
  val OPENING_CHEVRON = "<"
  val XMLPROLOGUE_START = "<?xml version"
  val XMLPROLOGUE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"

  def parser(maxSize: Option[Int] = None, insertPrologueIfNotPresent: Boolean = false):
  Flow[ByteString, ParsingData, NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(maxSize,insertPrologueIfNotPresent))
  }

  private class StreamingXmlParser(maxSize: Option[Int] = None,insertPrologueIfNotPresent: Boolean = false)
    extends GraphStage[FlowShape[ByteString, ParsingData]]
      with StreamHelper
      with ParsingDataFunctions {

    val in: Inlet[ByteString] = Inlet("XMLParser.in")
    val out: Outlet[ParsingData] = Outlet("XMLParser.out")
    override val shape: FlowShape[ByteString, ParsingData] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

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
          val pushedData = grab(in)
          if (isFirstChunk && pushedData.length > 0) {
            val data = pushedData.utf8String
            chunk = if (data.contains(OPENING_CHEVRON))
              ByteString(data.substring(data.indexOf(OPENING_CHEVRON))).toArray
            else pushedData.toArray

            if(insertPrologueIfNotPresent) {
              chunk = if (data.contains(XMLPROLOGUE_START)) chunk else ByteString(XMLPROLOGUE).toArray ++ chunk
            }
            parser.getInputFeeder.feedInput(chunk, 0, chunk.length)
            isFirstChunk = false
          } else {
            chunk = pushedData.toArray
            parser.getInputFeeder.feedInput(chunk, 0, chunk.length)
          }



          totalProcessedLength += streamBuffer.length
          totalProcessedLength += chunk.length
          (maxSize) match {
            case _ if totalProcessedLength == 0 => Failure(EmptyStreamError())
            case Some(size) if totalProcessedLength > size => Failure(MaxSizeError())
            case _ =>
              Try {
                advanceParser()
                totalProcessedLength -= incompleteBytes.length
                push(out, ParsingData(ByteString(streamBuffer.toArray), Set.empty, totalProcessedLength))
                streamBuffer.clear()
                streamBuffer ++= incompleteBytes
                incompleteBytes.clear()
                chunk = Array.empty[Byte]
              }
          }
        }

        def processOnUpstreamFinish(): Try[Unit] = {
          for {
            _ <- Try(advanceParser())
          } yield {
            if (tagsCounter != 0)
              emitStage(
                XMLElement(Nil, Map(MALFORMED_STATUS -> XML_START_END_TAGS_MISMATCH), Some(MALFORMED_STATUS)),
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
              )(ByteString(streamBuffer.toArray))

            else {
              emitStage(
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
              )(ByteString(streamBuffer.toArray))
            }

          }
        }

        private def emitStage(elementsToAdd: XMLElement*)(bytesToEmit: ByteString) = {
          emit(out, ParsingData(bytesToEmit, elementsToAdd.toSet, totalProcessedLength))
        }

        def processStage(f: () => Try[Unit]) = {
          f().recover {
            case e: WFCException =>
              emitStage(
                XMLElement(Nil, Map(MALFORMED_STATUS -> e.getMessage), Some(MALFORMED_STATUS)),
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
              )(ByteString(incompleteBytes.toArray ++ chunk))
              completeStage()
            case e: MaxSizeError =>
              emitStage(
                XMLElement(Nil, Map.empty, Some(STREAM_MAX_SIZE)),
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
              )(ByteString(incompleteBytes.toArray ++ chunk))
              completeStage()
            case e: EmptyStreamError =>
              emitStage(
                XMLElement(Nil, Map.empty, Some(STREAM_IS_EMPTY)),
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
              )(ByteString(Array.empty[Byte]))
              completeStage()
            case e: Throwable =>
              throw e
          }
        }

        var chunk = Array[Byte]()
        var totalProcessedLength = 0
        val streamBuffer = ArrayBuffer[Byte]()
        val incompleteBytes = ArrayBuffer[Byte]()
        val xmlElements = mutable.Set[XMLElement]()

        var tagsCounter = 0
        var isFirstChunk = true

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            val (start, end) = getBounds(parser)
            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                streamBuffer ++= chunk.slice(0, start)
                incompleteBytes ++= chunk.slice(start, chunk.length)
              case XMLStreamConstants.START_ELEMENT =>
                tagsCounter += 1
                advanceParser()
              case XMLStreamConstants.END_ELEMENT =>
                tagsCounter -= 1
                advanceParser()
              case x =>
                if (parser.hasNext) advanceParser()
            }
          }
        }

        private def getBounds(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): (Int, Int) = {
          val start = reader.getLocationInfo.getStartingByteOffset.toInt
          (
            if (start == 1) 0 else start - (totalProcessedLength - chunk.length),
            reader.getLocationInfo.getEndingByteOffset.toInt - (totalProcessedLength - chunk.length)
            )
        }
      }
  }

}
