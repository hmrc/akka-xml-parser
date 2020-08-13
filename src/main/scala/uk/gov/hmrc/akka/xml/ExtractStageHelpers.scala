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

import scala.collection.mutable.ArrayBuffer

trait ExtractStageHelpers {

  def update(xmlElementsLst: scala.collection.mutable.Set[XMLGroupElement],
             path: ArrayBuffer[String], newValue: Some[String]): Unit = {
    val elementsWithoutAnyValueForGivenPath = xmlElementsLst.collect {
      case e: XMLGroupElement if (e.xPath == path.toList) && e.value.isEmpty => e
    }

    elementsWithoutAnyValueForGivenPath.map((ele: XMLGroupElement) => {
      xmlElementsLst.remove(ele)
      val newElement = ele.copy(value = newValue)
      xmlElementsLst.add(newElement)
    })
  }

  def getCompletedXMLElements(xmlElementsLst: scala.collection.mutable.Set[XMLGroupElement]):
  scala.collection.mutable.Set[XMLGroupElement] = {
    val completedElements = xmlElementsLst.collect {
      case e if !(e.xPath.nonEmpty && e.value.isEmpty) => e
    }

    completedElements.foreach({
      xmlElementsLst -= _
    })

    completedElements
  }
  
}
