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

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.fasterxml.aalto.{AsyncByteBufferFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader}
import com.fasterxml.aalto.stax.InputFactoryImpl

import scala.annotation.tailrec

/**
  * Created by william on 14/03/17.
  */
object ElementCompletionFlow {

  def flow(implicit mat: Materializer): Flow[ByteString, ByteString, NotUsed] = {
    val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
    implicit val parser: AsyncXMLStreamReader[AsyncByteBufferFeeder] = feeder.createAsyncForByteBuffer()

    val initialData = ParserData(ByteString.empty)

    Flow[ByteString]
      .scan(initialData) { (data, chunk) =>
        parser.getInputFeeder.feedInput(chunk.toByteBuffer)
        processChunk(data.incompleteBytes ++ chunk, data)
      }.map(_.data)
  }

  @tailrec
  private def processChunk(chunk: ByteString, data: ParserData)(implicit parser: AsyncXMLStreamReader[AsyncByteBufferFeeder]): ParserData = {
    if(parser.hasNext) {
      val event = parser.next()
      val (eventStart, _) = getBounds(data, parser)
      event match {
        case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
          val incomplete = chunk.length - eventStart
          val clippedChunk = chunk.slice(0, eventStart)
          val incompleteBytes = chunk.slice(eventStart, chunk.length)
          data.copy(
            data = clippedChunk,
            totalProcessedLength = chunk.length - incomplete,
            incompleteBytes = incompleteBytes
          )
        case _ =>
          processChunk(chunk, data)
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
