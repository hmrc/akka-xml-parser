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
class XMLParser(instructions: Set[XMLInstruction]) extends StreamHelper {

  private lazy val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
  private lazy val parser: AsyncXMLStreamReader[AsyncByteBufferFeeder] = feeder.createAsyncForByteBuffer()

  def parse(source: Source[ByteString, NotUsed])(implicit mat: Materializer): Source[ParserData, NotUsed] = {
    val initialData = ParserData(ByteString.empty)

    source.scan(initialData) { (data, chunk) =>
      parser.getInputFeeder.feedInput(chunk.toByteBuffer)
      processChunk(chunk, instructions, data)
    }
  }

  @tailrec
  private def processChunk(chunk: ByteString, instructions: Set[XMLInstruction], data: ParserData): ParserData = {
    if(parser.hasNext) {
      val event = parser.next()

      event match {
        case AsyncXMLStreamReader.EVENT_INCOMPLETE => data.copy(chunk)
        case XMLStreamConstants.START_ELEMENT =>
          val currentPath = data.xPath :+ parser.getLocalName

          instructions.headOption match {
            case Some(XMLExtract(`currentPath`, attrs)) =>
              val matchedAttrs = getPredicateMatch(parser, attrs)
              processChunk(chunk, instructions, data.copy(
                xPath = currentPath,
                attributes = matchedAttrs
              ))
            case _ => processChunk(chunk, instructions, data.copy(xPath = currentPath))
          }
        case XMLStreamConstants.CHARACTERS =>
          val chars = data.characters match {
            case Some(s) => Some(s + parser.getText())
            case None => Some(parser.getText())
          }
          processChunk(chunk, instructions, data.copy(characters = chars))

        case XMLStreamConstants.END_ELEMENT =>
          val currentPath = data.xPath
          instructions.headOption match {
            case Some(XMLExtract(`currentPath`, _)) =>
              val chars = data.characters
              processChunk(chunk, instructions.tail, data.copy(
                elements = data.elements + XMLElement(currentPath, data.attributes, chars),
                xPath = currentPath.dropRight(1),
                characters = None,
                attributes = Map.empty
              ))
            case _ =>
              processChunk(chunk, instructions.tail, data.copy(xPath = currentPath.dropRight(1)))
          }
        case _ =>
          processChunk(chunk, instructions, data)
      }
    } else data
  }

}
