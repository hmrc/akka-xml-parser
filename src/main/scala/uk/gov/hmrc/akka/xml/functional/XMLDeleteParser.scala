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

package uk.gov.hmrc.akka.xml.functional

import javax.xml.stream.XMLStreamConstants

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.fasterxml.aalto.stax.InputFactoryImpl
import com.fasterxml.aalto.{AsyncByteBufferFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader}
import uk.gov.hmrc.akka.xml.{StreamHelper, XMLDelete, XMLInstruction}

import scala.annotation.tailrec

/**
  * Created by william on 13/03/17.
  */
class XMLDeleteParser extends StreamHelper {

  private lazy val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
  private lazy val parser: AsyncXMLStreamReader[AsyncByteBufferFeeder] = feeder.createAsyncForByteBuffer()

  def parse(instructions: Set[XMLInstruction])(implicit mat: Materializer): Flow[ByteString, ParserData, NotUsed] = {
    val initialData = ParserData(ByteString.empty, instructions)

    Flow[ByteString]
      .scan(initialData) { (data, chunk) =>
        parser.getInputFeeder.feedInput(chunk.toByteBuffer)
        processChunk(chunk, data.instructions, data.copy(
          size = chunk.length
        ))
      }
      .recover {
        case _ => ParserData(ByteString.empty)
      }
  }

  @tailrec
  private def processChunk(chunk: ByteString, instructions: Set[XMLInstruction], data: ParserData): ParserData = {
    if(parser.hasNext) {
      val event = parser.next()
      val (eventStart, eventEnd) = getBounds(data, parser)
      event match {
        case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
          val incomplete = chunk.length - eventStart
          data.copy(
            data = chunk.slice(data.chunkOffset, chunk.length),
            totalProcessedLength = chunk.length - incomplete,
            chunkOffset = 0
          )
        case XMLStreamConstants.START_ELEMENT =>
          val currentPath = data.xPath :+ parser.getLocalName
          val instr = instructions.collectFirst {
            case i@XMLDelete(`currentPath`) => i
          }
          instr match {
            case Some(XMLDelete(`currentPath`)) =>
              val clippedChunk = chunk.slice(data.chunkOffset, eventStart)
              processChunk(clippedChunk, instructions, data.copy(
                xPath = currentPath,
                chunkOffset = eventEnd
              ))
            case _ => processChunk(chunk, instructions, data.copy(xPath = currentPath))
          }
        case XMLStreamConstants.CHARACTERS =>
          val currentPath = data.xPath
          val instr = instructions.collectFirst {
            case i@XMLDelete(`currentPath`) => i
          }
          instr match {
            case Some(XMLDelete(`currentPath`)) =>
              val offset = data.chunkOffset + (eventEnd - eventStart)
              processChunk(chunk, instructions, data.copy(chunkOffset = offset))
            case _ => processChunk(chunk, instructions, data)
          }
        case XMLStreamConstants.END_ELEMENT =>
          val currentPath = data.xPath
          val instr = instructions.collectFirst {
            case i@XMLDelete(`currentPath`) => i
          }
          instr match {
            case Some(e@XMLDelete(`currentPath`)) =>
              val filtered = instructions.filter(_ != e)
              processChunk(chunk, filtered, data.copy(
                instructions = filtered,
                xPath = currentPath.dropRight(1),
                chunkOffset = eventEnd
              ))
            case _ =>
              processChunk(chunk, instructions, data.copy(xPath = currentPath.dropRight(1)))
          }
        case _ =>
          processChunk(chunk, instructions, data)
      }
    } else data
  }

  private def getBounds(data: ParserData, reader: AsyncXMLStreamReader[AsyncByteBufferFeeder]): (Int, Int) = {
    val start = reader.getLocationInfo.getStartingByteOffset.toInt
    (
      if (start == 1) 0 else start - (data.totalProcessedLength - data.data.length),
      reader.getLocationInfo.getEndingByteOffset.toInt - (data.totalProcessedLength - data.data.length)
    )
  }

}
