/*
 * Copyright 2021 HM Revenue & Customs
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
      case e: XMLElement if (e.xPath == path.toList) && e.value.isEmpty => e
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
      case e if !(e.xPath.nonEmpty && (e.value.isEmpty && e.attributes.isEmpty)) => e
    }

    completedElements.foreach({
      xmlElementsLst -= _
    })

    completedElements
  }


  def getUpdatedElement(xPath: Seq[String], attributes: Map[String, String], elemText: String)
                       (implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): String = {
    val prefix = getPrefix

    val startElement = attributes.foldLeft(s"<$prefix${xPath.last}") {
      case (s, (k, v)) => s"""$s $k="$v""""
    } + ">"
    val value = elemText
    val endElement = getEndElement(xPath, prefix)
    s"$startElement$value$endElement"
  }

  private def getPrefix(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): String = Option(reader.getPrefix) match {
    case Some(pre) if pre.nonEmpty => s"$pre:"
    case _ => ""
  }

  private def getEndElement(xPath: Seq[String], prefix: String) = s"</$prefix${xPath.last}>"

}
