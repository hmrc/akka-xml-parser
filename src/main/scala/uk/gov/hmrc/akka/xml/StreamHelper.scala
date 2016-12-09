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

import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLStreamReader}

import scala.collection.mutable.ArrayBuffer

/**
  * Created by abhishek on 02/12/16.
  */
trait StreamHelper {

  def update(xmlElementsLst: scala.collection.mutable.Set[XMLElement],
             path: ArrayBuffer[String], newValue: Some[String]): Unit = {
    val elementsWithoutAnyValueForGivenPath = xmlElementsLst.collect {
      case e: XMLElement if (e.xPath == path.toList) && (e.value == None) => e
    }

    elementsWithoutAnyValueForGivenPath.map((ele: XMLElement) => {
      xmlElementsLst.remove(ele)
      val newElement = ele.copy(value = newValue)
      xmlElementsLst.add(newElement)
    })

  }

  def getCompletedXMLElements(xmlElementsLst: scala.collection.mutable.Set[XMLElement]):
  scala.collection.mutable.Set[XMLElement] = {
    val completedElements = xmlElementsLst.collect {
      case e: XMLElement if !(e.xPath.size > 0 && e.value == None) => e
    }
    completedElements.foreach(x => {
      xmlElementsLst -= x
    })
    completedElements
  }

  def getPredicateMatch(parser: AsyncXMLStreamReader[AsyncByteArrayFeeder], predicates: Map[String, String]): Map[String, String] = {
    val XMLNS = "xmlns"
    val collection = scala.collection.mutable.Map[String, String]()

    if (parser.getNamespaceCount > 0 && predicates.keySet(XMLNS)) collection.+=(XMLNS -> parser.getNamespaceURI(0))
    (0 until parser.getNamespaceCount).map { i =>
      val ns = if (parser.getNamespacePrefix(i).length == 0) XMLNS else XMLNS + ":" + parser.getNamespacePrefix(i)
      if (predicates.keySet(ns)) {
        collection.+=(ns -> parser.getNamespaceURI(i))
      }
    }

    (0 until parser.getAttributeCount).map(i =>

      if (predicates.isEmpty) {
        collection.+=(parser.getAttributeLocalName(i) -> parser.getAttributeValue(i))
      } else if (predicates.keySet(parser.getAttributeLocalName(i)) || predicates.keySet(
        parser.getAttributePrefix(i) + ":" + parser.getAttributeLocalName(i))) {
        collection.+=(parser.getAttributeLocalName(i) -> parser.getAttributeValue(i))
      }
    )
    collection
  }.toMap



  def getUpdatedElement(xPath: Seq[String], attributes: Map[String, String], elemText: Option[String], isEmptyElement: Boolean)(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): String = {
    val prefix = getPrefix

    val startElement = attributes.foldLeft(s"<$prefix${xPath.last}") {
      case (s, (k, v)) => s"""$s $k="$v""""
    } + ">"

    val value = elemText.getOrElse("")

    val endElement = getEndElement(xPath, prefix)

    if (isEmptyElement) s"$startElement$value$endElement"
    else s"$startElement$value"
  }

  private def getPrefix(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): String = Option(reader.getPrefix) match {
    case Some(pre) if pre.nonEmpty => s"$pre:"
    case _ => ""
  }
  private def getEndElement(xPath: Seq[String], prefix: String) = s"</$prefix${xPath.last}>"

}
