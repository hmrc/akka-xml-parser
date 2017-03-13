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

import javax.xml.stream.XMLStreamConstants

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.fasterxml.aalto._
import com.fasterxml.aalto.stax.InputFactoryImpl

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
  * Created by william on 07/03/17.
  */
object TransformStage {

  def transform(instruction: XMLTransform): Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new Transformer(instruction))
  }

  private class Transformer(instruction: XMLTransform) extends GraphStage[FlowShape[ByteString, ByteString]] {

    val in: Inlet[ByteString] = Inlet("Transformer.in")
    val out: Outlet[ByteString] = Outlet("Transformer.out")
    override val shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        private val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
        private val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = feeder.createAsyncForByteArray()

        val node = ArrayBuffer[String]()

        var canWriteOutput = true
        var chunk = Array[Byte]()
        var totalProcessedLength = 0
        val streamBuffer = ArrayBuffer[Byte]()
        val incompleteBytes = ArrayBuffer[Byte]()
        val transformBuffer = ArrayBuffer[Byte]()

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
          chunk = grab(in).toArray
          totalProcessedLength += streamBuffer.length
          totalProcessedLength += chunk.length
          Try {
            parser.getInputFeeder.feedInput(chunk, 0 , chunk.length)
            advanceParser()
            totalProcessedLength -= incompleteBytes.length
            push(out, ByteString(streamBuffer.toArray))
            streamBuffer.clear()
            streamBuffer ++= incompleteBytes
            incompleteBytes.clear()
            chunk = Array.empty[Byte]
          }
        }

        def processOnUpstreamFinish(): Try[Unit] = {
          for {
            _ <- Try(advanceParser())
          } yield {
            emitStage(ByteString(streamBuffer.toArray))
          }
        }

        private def emitStage(bytesToEmit: ByteString) = {
          emit(out, bytesToEmit)
        }

        def processStage(f: () => Try[Unit]) = {
          f().recover {
            case e: WFCException =>
              emitStage(ByteString(incompleteBytes.toArray ++ chunk))
              completeStage()
            case e: MaxSizeError =>
              emitStage(ByteString(incompleteBytes.toArray ++ chunk))
              completeStage()
            case e: EmptyStreamError =>
              emitStage(ByteString.empty)
              completeStage()
            case e: Throwable =>
              throw e
          }
        }

        @tailrec
        private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            val (eventStart, eventEnd) = getBounds(parser)
            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                if(canWriteOutput) {
                  //streamBuffer ++= chunk.slice(0, eventStart)
                  incompleteBytes ++= chunk.slice(eventStart, chunk.length)
                }
              case XMLStreamConstants.START_ELEMENT =>
                node += parser.getLocalName
                instruction match {
                  case XMLTransform(`node`, _) =>
                    canWriteOutput = false
                    streamBuffer ++= chunk.slice(0, eventStart)
                  case _ =>
                }
                if(!canWriteOutput) {
                  transformBuffer ++= chunk.slice(eventStart, eventEnd)
                  println(new String(transformBuffer.toArray))
                }
                if (parser.hasNext) advanceParser()
              case XMLStreamConstants.CHARACTERS =>
                if(!canWriteOutput) {
                  transformBuffer ++= chunk.slice(eventStart, eventEnd)
                  println(new String(transformBuffer.toArray))
                }
                if (parser.hasNext) advanceParser()
              case XMLStreamConstants.END_ELEMENT =>
                if(!canWriteOutput) {
                  transformBuffer ++= chunk.slice(eventStart, eventEnd)
                  println(new String(transformBuffer.toArray))
                }
                instruction match {
                  case XMLTransform(`node`, transform) =>
                    canWriteOutput = true
                    val res = transform(new String(transformBuffer.toArray))
                    transformBuffer.clear()
                    streamBuffer ++= res.getBytes()
                  case _ =>
                }
                node -= parser.getLocalName
                if (parser.hasNext) advanceParser()
              case _ =>
                if (parser.hasNext) advanceParser()
            }
          }
        }

        private def getBounds(reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): (Int, Int) = {
          val start = reader.getLocationInfo.getStartingByteOffset.toInt
          (
            if (start == 1) 0 else start - (totalProcessedLength - chunk.length),
            reader.getLocationInfo.getEndingByteOffset.toInt - (totalProcessedLength - chunk.length)
          )
        }

      }
  }

}

