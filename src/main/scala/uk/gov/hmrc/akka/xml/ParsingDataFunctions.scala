/*
 * Copyright 2022 HM Revenue & Customs
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

import org.apache.pekko.util.ByteString

/**
  * Created by abhishek on 12/12/16.
  */
trait ParsingDataFunctions {

  def extractBytes(data: ByteString, from: Int, to: Int): ByteString = {
    data.slice(from, to)
  }

  def insertBytes(data: ByteString, offset: Int, at: Int, insert: Array[Byte]): ByteString = {
    data.slice(offset, at) ++ ByteString(insert)
  }

  def deleteBytes(data: ByteString, offset: Int, from: Int, to: Int): ByteString = {
    data.slice(offset, from) ++ data.slice(to, data.length)
  }

}


case class ParsingData(data: ByteString, extractedElements: Set[XMLElement], totalProcessedLength: Int)
