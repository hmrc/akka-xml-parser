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
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.fasterxml.aalto.{AsyncByteBufferFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader}
import com.fasterxml.aalto.stax.InputFactoryImpl

import scala.annotation.tailrec

/**
  * Created by william on 18/02/17.
  */
class XMLParser(instructions: Set[XMLInstruction]) {

  private lazy val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
  private lazy val parser: AsyncXMLStreamReader[AsyncByteBufferFeeder] = feeder.createAsyncForByteBuffer()

  def parse(source: Source[ByteString, NotUsed])(implicit mat: Materializer): Source[ParserData, NotUsed] = {
    val initialData = ParserData(ByteString.empty, instructions)

    source.map { chunk =>
      parser.getInputFeeder.feedInput(chunk.toByteBuffer)
      processChunk(chunk, instructions, initialData)
    }
  }

  @tailrec
  private def processChunk(chunk: ByteString, instructions: Set[XMLInstruction], data: ParserData, xPath: Seq[String] = Seq.empty): ParserData = {
    if(parser.hasNext) {
      val event = parser.next()

      event match {
        case AsyncXMLStreamReader.EVENT_INCOMPLETE => data.copy(chunk)
        case XMLStreamConstants.START_ELEMENT =>
          println("parser >>> Start element")
          val currentPath: Seq[String] = xPath :+ parser.getLocalName
          instructions.headOption match {
            case Some(XMLExtract(`currentPath`, _)) =>
              println(currentPath)
              processChunk(chunk,instructions, data, currentPath)
            case _ => processChunk(chunk, instructions, data, currentPath)
          }
        case XMLStreamConstants.END_ELEMENT =>
          println("parser >>> End element")
          instructions.headOption match {
            case Some(XMLExtract(_, _)) =>
              println(xPath)
              processChunk(chunk, instructions.tail, data, xPath.dropRight(1))
            case _ =>
              processChunk(chunk, instructions, data, xPath)
          }
        case XMLStreamConstants.CHARACTERS =>
          println("parser >>> Characters")
          println(parser.getText())
          processChunk(chunk, instructions, data)
        case _ =>
          processChunk(chunk, instructions, data)
      }
    } else data
  }

}
