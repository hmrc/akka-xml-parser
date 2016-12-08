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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future

/**
  * Created by abhishek on 23/09/16.
  */
class XMLParserXmlExtractSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually {

  implicit val system = ActorSystem("XMLParser")
  implicit val mat = ActorMaterializer()

  def parse(instructions: Set[XMLInstruction]) = Flow[ByteString]
    .via(AkkaXMLParser.parser(instructions))
    .via(flow)
    .toMat(collectXMLElements)(Keep.right)


  def flow = Flow[(ByteString, Set[XMLElement])].map(x => x._2)

  def collectXMLElements: Sink[Set[XMLElement], Future[Set[XMLElement]]] =
    Sink.fold[Set[XMLElement], Set[XMLElement]](Set.empty)((a, b) => {
      a ++ b
    })

  "AkkaXMLParser#parser" should "pass the original XML through to any iteratees" in {
    1 === 1
  }

  it should "extract a single value from a valid xml" in {
    val source = Source.single(ByteString("<xml><header><id>12345</id></header></xml>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")))
    }
  }

  it should "extract a single value when the bytes are split" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>12"),
      ByteString("3"),
      ByteString("45</id></header></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")))
    }
  }

  it should "extract the ID when the bytes are split and there are other elements at the same level" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>12"),
      ByteString("3"),
      ByteString("45</id><name>Hello</name></header></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")))
    }
  }

  it should "handle a malformed xml with no available metadata" in {
    val source = Source.single(ByteString("malformed"))

    whenReady(source.runWith(parse(Set.empty))) { r =>
      r shouldBe Set(XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS)))
    }
  }


  it should "return any already extracted metadata on a malformed xml" in {
    val source = Source.single(ByteString("<xml><header><id>12345</id></xml>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")),
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS)))
    }
  }



  it should "extract available metadata if the xml is malformed after the first chunk" in {
    val source = Source(List(ByteString("<xml><header><id>12345</id>"), ByteString("</xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")),
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS))
      )
    }
  }

  it should "return a malformed status if an error occurs in the middle of a chunk, leaving unprocessed bytes" in {
    val source = Source(List(ByteString("<header>brokenID</brokenTag><moreBytes/>"), ByteString("</header>")))
    val paths = Set.empty[XMLInstruction]

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS))
      )
    }
  }

  it should "extract attributes where the xPath is given" in {
    val source = Source.single(ByteString("<xml><body><element Attribute=\"Test\">elementText</element></body></xml>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "body", "element"), Map("Attribute" -> "Test")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "body", "element"), Map("Attribute" -> "Test"), Some("elementText")
        )
      )
    }
  }

  it should "extract attributes when a given namespace is present" in {
    val source = Source.single(ByteString("<xml Attribute=\"Test\" Attribute2=\"Test2\"></xml>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml"), Map("Attribute2" -> "Test2")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("Attribute2" -> "Test2"), Some(""))
      )
    }
  }

  it should "not extract attributes where the xPath matches but the attributes differ" in {
    val source = Source.single(ByteString("<xml><body><element Attribute=\"notTest\">elementText</element></body></xml>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id"), Map("Attribute" -> "notTest")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set.empty
    }
  }

  it should "extract an element with no characters" in {
    val source = Source(List(ByteString("<xml type=\"test\"><bo"), ByteString("dy><foo>test</fo"),
      ByteString("o><bar>test</bar></body></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml"), Map("type" -> "test")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml"), Map("type" -> "test"), Some("")))
    }
  }

  it should "extract only the element with an xmlns attribute when the xmlns namespace is supplied" in {
    val source = Source(List(ByteString("<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\"><bo"),
      ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")))
    }
  }

  it should "honour any prefixed namespace if the basic one is not required" in {
    val source = Source.single(ByteString("<gt:GovTalkMessage xmlns:gt=\"http://www.govtalk.gov.uk/CM/envelope\"><gt:EnvelopeVersion>2.0</gt:EnvelopeVersion></gt:GovTalkMessage>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("GovTalkMessage"), Map("xmlns:gt" -> "http://www.govtalk.gov.uk/CM/envelope")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("GovTalkMessage"), Map("xmlns:gt" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")))
    }
  }

  it should "extract the xmlns element when other namespaces exist" in {
    val source = Source(List(ByteString("<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo"),
      ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")))

    whenReady(source.runWith(parse(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), Some(""))
      )
    }
  }

  it should "extract non-namespace attributes then just the xmlns attribute when required" in {
    val source = Source(List(ByteString("<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" " +
      "xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo"),
      ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Set[XMLInstruction](
      XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")),
      XMLExtract(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"))
    )

    whenReady(source.runWith(parse(paths))) { r =>
      println(r)
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")),
        XMLElement(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"), Some(""))
      )
    }
  }

}
