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

import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLStreamReader}

/*
 * Created by gabor dorcsinecz and joseph griffiths on 07/08/17.
 */
object ExtractNameSpace {

  def apply(parser: AsyncXMLStreamReader[AsyncByteArrayFeeder], targetNameSpace: Map[String, String]): Map[String, String] = {
    val XMLNS = "xmlns"
    val extractedNameSpaces = scala.collection.mutable.Map[String, String]()

    (0 until parser.getNamespaceCount).foreach { currentNS =>
      val ns: String = if (parser.getNamespacePrefix(currentNS).length == 0) XMLNS else XMLNS + ":" + parser.getNamespacePrefix(currentNS)

      compareNameSpaceToTarget(targetNameSpace, currentNS, ns)
    }

    (0 until parser.getAttributeCount).foreach { i =>
      if (targetNameSpace.isEmpty) {
        extractedNameSpaces += (parser.getAttributeLocalName(i) -> parser.getAttributeValue(i))
      } else if (targetNameSpace.keySet(parser.getAttributeLocalName(i)) || targetNameSpace.keySet(
        parser.getAttributePrefix(i) + ":" + parser.getAttributeLocalName(i))) {
        extractedNameSpaces += (parser.getAttributeLocalName(i) -> parser.getAttributeValue(i))
      }
    }

    def compareNameSpaceToTarget(targetNameSpace: Map[String, String], i: Int, ns: String) = {
      targetNameSpace.foreach {
        case (k, v) =>
          if (v == parser.getNamespaceURI(i)) {
            extractedNameSpaces += (ns -> parser.getNamespaceURI(i))
          }
      }
    }
    extractedNameSpaces.toMap
  }
}
