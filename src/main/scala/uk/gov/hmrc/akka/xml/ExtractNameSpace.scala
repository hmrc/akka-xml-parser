/*
 * Copyright 2018 HM Revenue & Customs
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

import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLStreamReader}

/*
 * Created by gabor dorcsinecz and joseph griffiths on 07/08/17.
 */
object ExtractNameSpace {

  def apply(parser: AsyncXMLStreamReader[AsyncByteArrayFeeder], targetNameSpace: Map[String, String]): Map[String, String] = {
    val XMLNS = "xmlns"
    val extractedNameSpaces = scala.collection.mutable.Map[String, String]()

    (0 until parser.getNamespaceCount).foreach { index =>
      val ns = if (parser.getNamespacePrefix(index).length == 0) XMLNS else XMLNS + ":" + parser.getNamespacePrefix(index)

      upsertMatchingNameSpace(targetNameSpace, ns, index)
    }

    (0 until parser.getAttributeCount).foreach { index =>
      if (targetNameSpace.isEmpty) {
        extractedNameSpaces += (parser.getAttributeLocalName(index) -> parser.getAttributeValue(index))
      } else if (targetNameSpace.keySet(parser.getAttributeLocalName(index)) || targetNameSpace.keySet(
        parser.getAttributePrefix(index) + ":" + parser.getAttributeLocalName(index))) {
        extractedNameSpaces += (parser.getAttributeLocalName(index) -> parser.getAttributeValue(index))
      }
    }

    def upsertMatchingNameSpace(targetNameSpace: Map[String, String], ns: String, index: Int) = {
      targetNameSpace.foreach {
        case (k, v) =>
          if (v == parser.getNamespaceURI(index))
            extractedNameSpaces += (ns -> parser.getNamespaceURI(index))
      }
    }
    extractedNameSpaces.toMap
  }
}
