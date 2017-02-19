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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Future

/**
  * Created by william on 18/02/17.
  */
class XMLParserSpec extends WordSpec
  with Matchers with ScalaFutures with BeforeAndAfterAll {

  private implicit val sys = ActorSystem("xml-parser")
  private implicit val mat = ActorMaterializer()

  override def afterAll(): Unit = {
    sys.terminate()
  }

  "parse" should {

    "return a source which, when run, will produce a ParserData instance" in {

      val xmlSrc = Source(
        List(
          ByteString("<hello"), ByteString("world>"),
          ByteString("foo"), ByteString("bar"),
          ByteString("</helloworld>")
        )
      )

      val parser = new XMLParser(Set(XMLExtract(XPath("helloworld"))))

      val res: Future[ByteString] = parser.parse(xmlSrc).map(_.data)
        .runFold(ByteString.empty)(_ ++ _)

      whenReady(res) {
        _.utf8String shouldBe "<helloworld>foobar</helloworld>"
      }
    }

  }

}
