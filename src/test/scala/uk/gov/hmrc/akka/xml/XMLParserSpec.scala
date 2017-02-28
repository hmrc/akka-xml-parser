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
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
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

    "return a source which, when ran into a sink, will produce a ByteString" in {
      val xmlSrc = Source(
        List(
          ByteString("<hello"), ByteString("world>"),
          ByteString("foo"), ByteString("bar"),
          ByteString("</helloworld>")
        )
      )

      val parser = new XMLParser(Set(XMLExtract(XPath("helloworld"))))

      val sink: Sink[ParserData, Future[ByteString]] = Flow[ParserData]
        .map(_.data)
        .toMat(Sink.fold(ByteString.empty)(_ ++ _))(Keep.right)

      val res: Future[ByteString] = parser.parse(xmlSrc)
        .runWith(sink)

      whenReady(res) {
        _.utf8String shouldBe "<helloworld>foobar</helloworld>"
      }
    }

    "return a source which, when ran into a sink, will produce a Set of the extracted elements" in {
      val xmlSrc = Source(
        List(
          ByteString("""<root xmlns="http://www.govtalk.gov.uk/CM/envelope">"""),
          ByteString("<hello"), ByteString("""world foo="bar">"""),
          ByteString("foo"), ByteString("bar"),
          ByteString("</helloworld>"),
          ByteString("<hello"), ByteString("""world foo="bar">"""),
          ByteString("foo"), ByteString("bar"),
          ByteString("</helloworld>"),
          ByteString("</root>")
        )
      )

      val instructions = Set[XMLInstruction](
        XMLExtract(XPath("root"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")),
        XMLExtract(XPath("root/helloworld"), Map("foo" -> "bar"))
      )

      val parser = new XMLParser(instructions)

      val sink: Sink[ParserData, Future[Set[XMLElement]]] = Flow[ParserData]
        .map(_.elements)
        .toMat(Sink.fold(Set.empty[XMLElement])(_ ++ _))(Keep.right)

      val res: Future[Set[XMLElement]] = parser.parse(xmlSrc)
        .runWith(sink)

      whenReady(res) {
        _ shouldBe Set(
          XMLElement(XPath("root")),
          XMLElement(XPath("root/helloworld"), value = Some("foobar"))
        )
      }
    }

    "return a source which, when ran into a sink, will produce the total size of the Source data" in {
      val xmlSrc = Source(
        List(
          ByteString("<root>"),
          ByteString("</root>")
        )
      )

      val EXPECTED_SIZE = 13

      val parser = new XMLParser(Set(XMLExtract(XPath("root"))))

      val sink: Sink[ParserData, Future[Int]] = Flow[ParserData]
        .map(_.size)
        .toMat(Sink.fold(0)(_ + _))(Keep.right)

      val res: Future[Int] = parser.parse(xmlSrc)
        .runWith(sink)

      whenReady(res) {
        _ shouldBe EXPECTED_SIZE
      }
    }

  }

}
