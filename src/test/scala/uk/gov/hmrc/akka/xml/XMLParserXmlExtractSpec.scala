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

import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by abhishek on 23/09/16.
  */
class XMLParserXmlExtractSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {

  val f = fixtures

  import f._

  behavior of "CompleteChunkStage#parser"

  it should "extract a single value from a valid xml" in {
    val source = Source.single(ByteString("ï»¿<xml><header><id>12345</id></header></xml>"))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "42"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header></xml>"
    }
  }

  it should "extract a single xmlBlock from a valid xml when extractBlock is true" in {
    val source = Source.single(ByteString("ï»¿<xml><header><id><foo>foo</foo><bar>bar</bar></id></header></xml>"))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id"), Map.empty, true))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("<id><foo>foo</foo><bar>bar</bar></id>")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "65"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id><foo>foo</foo><bar>bar</bar></id></header></xml>"
    }
  }

  it should "extract max size value when the bytes are split - xml starts with illegal texts" in {
    val source = Source(List(ByteString("11111111<xml><header><i"),
      ByteString("d>12"),
      ByteString("3"),
      ByteString("45</id>"),
      ByteString("</header></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths, Some(50)))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "42"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header></xml>"
    }
  }

  it should "empty size value when source is empty" in {
    val source = Source.single(ByteString(""))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))
    whenReady(source.runWith(parseToXMLElements(paths, Some(40)))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "0"), Some(CompleteChunkStage.STREAM_SIZE)),
        XMLElement(List(), Map.empty, Some(CompleteChunkStage.STREAM_IS_EMPTY))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe ""
    }
  }

  it should "retrun NO_VALIDATION_FOUND_FAILURE when no validation was performed till validationMaxSize" in {
    val source = Source.single(ByteString(""))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))
    whenReady(source.runWith(parseToXMLElements(paths, Some(40)))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "0"), Some(CompleteChunkStage.STREAM_SIZE)),
        XMLElement(List(), Map.empty, Some(CompleteChunkStage.STREAM_IS_EMPTY))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe ""
    }
  }


  it should "extract a single xmlblock when the bytes are split and extractBlock is true" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d><foo"),
      ByteString(">fo"), ByteString("o</foo"), ByteString("><bar"), ByteString(">bar"),
      ByteString("</bar></id></header></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id"), Map.empty, true))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("<id><foo>foo</foo><bar>bar</bar></id>")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "65"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id><foo>foo</foo><bar>bar</bar></id></header></xml>"
    }
  }

  it should "extract a single value when the bytes are split and element is empty" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>"),
      ByteString("</id></header></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "37"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header></xml>"
    }
  }

  it should "xml is unaltered if there is an unrelated instruction" in {
    val source = Source(List(ByteString("<xml><header><id>"),
      ByteString("</id></header><body/>"),
      ByteString("</xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "idfake")))

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header><body/></xml>"
    }
  }

  it should "xml is unaltered if there is no instruction and tags are split in chunks" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>"),
      ByteString("</id></header></xml>")))

    whenReady(source.runWith(parseToByteString(Seq[XMLInstruction]()))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header></xml>"
    }
  }

  it should "xml is unaltered if there is no instruction and tags are not split in chunks" in {
    val source = Source(List(ByteString("<xml><header><id>"),
      ByteString("</id></header></xml>")))

    whenReady(source.runWith(parseToByteString(Seq[XMLInstruction]()))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header></xml>"
    }
  }


  it should "xml is unaltered if there is an unrelated instruction and tags are not split in chunks" in {
    val source = Source(List(ByteString("<xml><header><id>"),
      ByteString("</id></header></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "idfake")))

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header></xml>"
    }
  }

  it should "xml is unaltered if there is an unrelated instruction and data in in one chunk" in {
    val source = Source(List(ByteString("<xml><header><id></id></header></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "idfake")))

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header></xml>"
    }
  }




  it should "extract a single value when the bytes are split and element is whitespace" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>  "),
      ByteString("  </id></header></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "41"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>    </id></header></xml>"
    }
  }

  it should "extract the bytes are split and there are other elements at the same level" in {
    val source = Source(List(ByteString("<xml><header><id>12"),
      ByteString("345</id><name>"),
      ByteString("He"),
      ByteString("llo</name></header></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "60"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id><name>Hello</name></header></xml>"
    }
  }

  it should "extract the block when the bytes are split and there are other elements at the same level" in {
    val source = Source(List(ByteString("<xml><header><id><foo>foo</foo><b"),
      ByteString("ar>bar</bar></id><name>"),
      ByteString("He"),
      ByteString("llo</name></header></xml>")))

    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id"), Map.empty, true))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("<id><foo>foo</foo><bar>bar</bar></id>")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "83"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id><foo>foo</foo><bar>bar</bar></id><name>Hello</name></header></xml>"
    }
  }



  it should "extract the ID when the bytes are split and there are other elements at the same level" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>12"),
      ByteString("3"),
      ByteString("45</id><name>"),
      ByteString("He"),
      ByteString("llo</name></header></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "60"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id><name>Hello</name></header></xml>"
    }
  }


  it should "handle a malformed xml with no available metadata" in {
    val source = Source.single(ByteString("malformed"))

    whenReady(source.runWith(parseToXMLElements(Seq[XMLInstruction]()))) { r =>
      r.head.attributes(CompleteChunkStage.MALFORMED_STATUS) contains ("Unexpected character 'm' (code 109)")
    }

    whenReady(source.runWith(parseToByteString(Seq[XMLInstruction]()))) { r =>
      r.utf8String shouldBe "malformed"
    }
  }

  it should "return any already extracted metadata on a malformed xml and original xml should still be returned" in {
    val source = Source.single(ByteString("<xml><header><id>12345</id></xml>"))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r.toSeq(0) shouldBe XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345"))
      r.toSeq(1).attributes(CompleteChunkStage.MALFORMED_STATUS) contains ("Unexpected end tag: expected")
    }

    whenReady(source.runWith(parseToByteString(Seq[XMLInstruction]()))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></xml>"
    }
  }


  it should "extract available metadata if the xml is malformed after the first chunk" in {
    val source = Source(List(ByteString("<xml><header><id>12345</id>"), ByteString("</xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r.toSeq(0) shouldBe XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345"))
      r.toSeq(1).attributes(CompleteChunkStage.MALFORMED_STATUS) contains ("Unexpected end tag: expected")
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></xml>"
    }
  }

  it should "return a malformed status if an error occurs in the middle of a chunk, leaving unprocessed bytes" in {
    val source = Source(List(ByteString("<header>brokenID</brokenTag><moreBytes/>"), ByteString("</header>")))
    val paths = Seq[XMLInstruction]()

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r.head.attributes(CompleteChunkStage.MALFORMED_STATUS) contains ("Unexpected end tag: expected")
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<header>brokenID</brokenTag><moreBytes/>"
    }
  }

  it should "return a malformed status if an error occurs in the last chunk, leaving unprocessed bytes" in {
    val source = Source(List(ByteString("<header><moreBytes/>"), ByteString("</header1111>")))
    val paths = Seq[XMLInstruction]()

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r.head.attributes(CompleteChunkStage.MALFORMED_STATUS) contains ("Unexpected end tag: expected")
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<header><moreBytes/></header1111>"
    }
  }

  it should "extract attributes where the xPath is given" in {
    val source = Source.single(ByteString("<xml><body><element Attribute=\"Test\">elementText</element></body></xml>"))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "body", "element"), Map("Attribute" -> "Test")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "71"), Some(CompleteChunkStage.STREAM_SIZE)),
        XMLElement(Seq("xml", "body", "element"), Map("Attribute" -> "Test"), Some("elementText")
        )
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><element Attribute=\"Test\">elementText</element></body></xml>"
    }
  }

  it should "extract attributes when a given namespace is present" in {
    val source = Source.single(ByteString("<xml Attribute=\"Test\" Attribute2=\"Test2\"></xml>"))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml"), Map("Attribute2" -> "Test2")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "47"), Some(CompleteChunkStage.STREAM_SIZE)),
        XMLElement(Seq("xml"), Map("Attribute2" -> "Test2"), Some(""))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml Attribute=\"Test\" Attribute2=\"Test2\"></xml>"
    }
  }

  it should "not extract attributes where the xPath matches but the attributes differ" in {
    val source = Source.single(ByteString("<xml><body><element Attribute=\"notTest\">elementText</element></body></xml>"))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml", "header", "id"), Map("Attribute" -> "notTest")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "74"), Some(CompleteChunkStage.STREAM_SIZE)))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><element Attribute=\"notTest\">elementText</element></body></xml>"
    }
  }

  it should "extract an element with no characters" in {
    val source = Source(List(ByteString("<xml type=\"test\"><bo"), ByteString("dy><foo>test</fo"),
      ByteString("o><bar>test</bar></body></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml"), Map("type" -> "test")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml"), Map("type" -> "test"), None),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "66"), Some(CompleteChunkStage.STREAM_SIZE)))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml type=\"test\"><body><foo>test</foo><bar>test</bar></body></xml>"
    }
  }

  it should "extract only the element with an xmlns attribute when the xmlns namespace is supplied" in {
    val source = Source(List(ByteString("<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\"><bo"),
      ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), None),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "100"), Some(CompleteChunkStage.STREAM_SIZE)))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\"><body><foo>test</foo><bar>test</bar></body></xml>"
    }
  }

  it should "honour any prefixed namespace if the basic one is not required" in {
    val source = Source.single(ByteString("<gt:GovTalkMessage xmlns:gt=\"http://www.govtalk.gov.uk/CM/envelope\"><gt:EnvelopeVersion>2.0</gt:EnvelopeVersion></gt:GovTalkMessage>"))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("GovTalkMessage"), Map("xmlns:gt" -> "http://www.govtalk.gov.uk/CM/envelope")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("GovTalkMessage"), Map("xmlns:gt" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "132"), Some(CompleteChunkStage.STREAM_SIZE)))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<gt:GovTalkMessage xmlns:gt=\"http://www.govtalk.gov.uk/CM/envelope\"><gt:EnvelopeVersion>2.0</gt:EnvelopeVersion></gt:GovTalkMessage>"
    }
  }

  it should "extract the xmlns element when other namespaces exist" in {
    val source = Source(List(ByteString("<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo"),
      ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Seq[XMLInstruction](XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), None),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "236"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo" +
        "dy><foo>test</foo><bar>test</bar></body></xml>"
    }
  }

  it should "extract non-namespace attributes then just the xmlns attribute when required" in {
    val source = Source(List(ByteString("<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" " +
      "xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo"),
      ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")),
      XMLExtract(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), None),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "236"), Some(CompleteChunkStage.STREAM_SIZE)),
        XMLElement(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"), None)
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" " +
        "xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" " +
        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo" +
        "dy><foo>test</fo" + "o><bar>test</bar></body></xml>"
    }
  }

  it should "only extract the specified xmlns attributes" in {
    val source = Source(List(ByteString("<xml " +
      "xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" " +
      "xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo"), ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")),
      XMLExtract(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), None),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "236"), Some(CompleteChunkStage.STREAM_SIZE)),
        XMLElement(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"), None)
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }
  }

  it should "only extract one specified xmlns attributes" in {
    val source = Source(List(ByteString("<xml " +
      "xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" " +
      "xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo"), ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"))
      // We deliberately exclude one extract - XMLExtract(Seq("xml"), Map("xsi:schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"))
      //XMLExtract(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), None),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "236"), Some(CompleteChunkStage.STREAM_SIZE))
        // We shouldn't see this one - XMLElement(Seq("xml"), Map("xsi:schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"), Some("")),
        //XMLElement(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"), Some(""))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }
  }

  it should "allow attributes to be extracted without needing to specify a namespace" in {
    val source = Source(List(ByteString("<xml " +
      "xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" " +
      "xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo"), ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")),
      XMLExtract(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"))
      //XMLExtract(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), None),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "236"), Some(CompleteChunkStage.STREAM_SIZE)),
        XMLElement(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"), None)
        //XMLElement(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"), Some(""))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }

  }


  it should "parse a complex namespace structure " in {

    val source = Source(List(ByteString("<gt:GovTalkMessage xmlns:gt=\"http://www.govtalk\">"), ByteString(
      "<gt:EnvelopeVersion>2.0</gt:EnvelopeVersion>"), ByteString(
      "<gt:Header>"), ByteString(
      "<gt:MessageDetails>"), ByteString(
      "<gt:Class>HMRC-CT-CT600</gt:Class><gt:Qualifier>response</gt:Qualifier>"), ByteString(
      "<gt:Function>submit</gt:Function><gt:CorrelationID>12345678</gt:CorrelationID>"), ByteString(
      "<gt:ResponseEndPoint></gt:ResponseEndPoint><gt:Transformation>XML</gt:Transformation>"), ByteString(
      "<gt:GatewayTest></gt:GatewayTest>"), ByteString(
      "</gt:MessageDetails>"), ByteString(
      "<gt:SenderDetails><gt:IDAuthentication>"), ByteString(
      "<gt:SenderID>user1</gt:SenderID><gt:Authentication><gt:Method>clear</gt:Method>"), ByteString(
      "<gt:Role>Authenticate/Validate</gt:Role><gt:Value>pass</gt:Value></gt:Authentication>"), ByteString(
      "</gt:IDAuthentication><gt:EmailAddress></gt:EmailAddress></gt:SenderDetails>"), ByteString(
      "</gt:Header><gt:GovTalkDetails>"), ByteString(
      "<gt:Keys></gt:Keys><gt:ChannelRouting><gt:Channel><gt:URI>1352</gt:URI>"), ByteString(
      "<gt:Product>ASPIRE HMRC-VAT100-DEC</gt:Product><gt:Version>1.0</gt:Version></gt:Channel>"), ByteString(
      "</gt:ChannelRouting></gt:GovTalkDetails>"), ByteString(
      "</gt:GovTalkMessage>"))
    )


    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns" -> "http://www.govtalk")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Qualifier")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Function")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "CorrelationID")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "TransactionID"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("HMRC-CT-CT600")),
      XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "939"), Some(CompleteChunkStage.STREAM_SIZE)),
      XMLElement(List("GovTalkMessage"), Map("xmlns:gt" -> "http://www.govtalk"), None),
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Function"), Map(), Some("submit")),
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Qualifier"), Map(), Some("response")),
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "CorrelationID"), Map(), Some("12345678"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe expected
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }

  }


  it should "handle empty element tags " in {
    val source = Source(List(ByteString(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><GovTalkMessage xmlns=\"http://www.govtalk.gov.uk"), ByteString(
      "/CM/envelope\"><EnvelopeVersion>2.0</EnvelopeVersion><Header><MessageDetails><Class></Class><Qualifier>"), ByteString(
      "request</Qualifier><Function>submit</Function><TransactionID></TransactionID><CorrelationID>74747474"), ByteString(
      "</CorrelationID><Transformation>XML</Transformation><GatewayTest>0</GatewayTest></MessageDetails>"), ByteString(
      "<SenderDetails><IDAuthentication><SenderID>user1</SenderID><Authentication><Method>clear</Method>"), ByteString(
      "<Role>principal</Role><Value>pass</Value></Authentication></IDAuthentication></SenderDetails></Header>"), ByteString(
      "<GovTalkDetails><Keys><Key Type=\"TestKey\">Retry2</Key></Keys><TargetDetails><Organisation>"), ByteString(
      "CapGemini</Organisation></TargetDetails><ChannelRouting><Channel><URI>1192</URI><Product>"), ByteString(
      "HMRC CT600</Product><Version>1.0.1</Version></Channel></ChannelRouting></GovTalkDetails>"), ByteString(
      "<Body></Body></GovTalkMessage>")))

    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("")),
      XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "881"), Some(CompleteChunkStage.STREAM_SIZE))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe expected
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }

  }

  it should "not cause an error if no namespace attribute exists" in {

    val source = Source(List(ByteString(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><GovTalkMessage><EnvelopeVersion>2.0</EnvelopeVersion>"), ByteString(
      "<Header><MessageDetails><Class>HMRC-CT-CT600</Class><Qualifier>request</Qualifier><Function>submit"), ByteString(
      "</Function><TransactionID></TransactionID><CorrelationID>454545454</CorrelationID><Transformation>"), ByteString(
      "XML</Transformation><GatewayTest>0</GatewayTest></MessageDetails><SenderDetails><IDAuthentication>"), ByteString(
      "<SenderID>user1</SenderID><Authentication><Method>clear</Method><Role>principal</Role>"), ByteString(
      "<Value>pass</Value></Authentication></IDAuthentication></SenderDetails></Header><GovTalkDetails>"), ByteString(
      "<Keys><Key Type=\"TestKey\">Retry2</Key></Keys> <TargetDetails><Organisation>CapGemini</Organisation>"), ByteString(
      "</TargetDetails><ChannelRouting><Channel><URI>1192</URI> <Product>HMRC CT600</Product>"), ByteString(
      "<Version>1.0.1</Version></Channel></ChannelRouting></GovTalkDetails><Body></Body>"), ByteString(
      "</GovTalkMessage>"))
    )


    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("HMRC-CT-CT600")),
      XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "851"), Some(CompleteChunkStage.STREAM_SIZE))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe expected
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }

  }


  it should "not cause an error if the namespace is requested but does not exist " in {

    val source = Source(List(ByteString(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><GovTalkMessage><EnvelopeVersion>2.0</EnvelopeVersion>"), ByteString(
      "<Header><MessageDetails><Class>HMRC-CT-CT600</Class><Qualifier>request</Qualifier><Function>submit"), ByteString(
      "</Function><TransactionID></TransactionID><CorrelationID>454545454</CorrelationID><Transformation>"), ByteString(
      "XML</Transformation><GatewayTest>0</GatewayTest></MessageDetails><SenderDetails><IDAuthentication>"), ByteString(
      "<SenderID>user1</SenderID><Authentication><Method>clear</Method><Role>principal</Role>"), ByteString(
      "<Value>pass</Value></Authentication></IDAuthentication></SenderDetails></Header><GovTalkDetails>"), ByteString(
      "<Keys><Key Type=\"TestKey\">Retry2</Key></Keys> <TargetDetails><Organisation>CapGemini</Organisation>"), ByteString(
      "</TargetDetails><ChannelRouting><Channel><URI>1192</URI> <Product>HMRC CT600</Product>"), ByteString(
      "<Version>1.0.1</Version></Channel></ChannelRouting></GovTalkDetails><Body></Body>"), ByteString(
      "</GovTalkMessage>")))


    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class")),
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns" -> "http://www.govtalk"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("HMRC-CT-CT600")),
      XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "851"), Some(CompleteChunkStage.STREAM_SIZE))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe expected
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }

  }

  it should "extract the element value, the attribute name and its value if specified" in {
    val source = Source(List(ByteString(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><GovTalkMessage><EnvelopeVersion>2.0</EnvelopeVersion>"), ByteString(
      "<Header><MessageDetails><Class>HMRC-CT-CT600</Class><Qualifier>request</Qualifier><Function>submit"), ByteString(
      "</Function><TransactionID></TransactionID><CorrelationID>454545454</CorrelationID><Transformation>"), ByteString(
      "XML</Transformation><GatewayTest>0</GatewayTest></MessageDetails><SenderDetails><IDAuthentication>"), ByteString(
      "<SenderID>user1</SenderID><Authentication><Method>clear</Method><Role>principal</Role>"), ByteString(
      "<Value>pass</Value></Authentication></IDAuthentication></SenderDetails></Header><GovTalkDetails>"), ByteString(
      "<Keys><Key Type=\"TestKey\">Retry2</Key><Key Type=\"TestKey5\">Retry5</Key></Keys> <TargetDetails><Organisation>CapGemini</Organisation>"), ByteString(
      "</TargetDetails><ChannelRouting><Channel><URI>1192</URI> <Product>HMRC CT600</Product>"), ByteString(
      "<Version>1.0.1</Version></Channel></ChannelRouting></GovTalkDetails><Body></Body>"), ByteString(
      "</GovTalkMessage>"))
    )
    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "GovTalkDetails", "Keys", "Key"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "GovTalkDetails", "Keys", "Key"), Map("Type" -> "TestKey"), Some("Retry2")),
      XMLElement(List("GovTalkMessage", "GovTalkDetails", "Keys", "Key"), Map("Type" -> "TestKey5"), Some("Retry5")),
      XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "884"), Some(CompleteChunkStage.STREAM_SIZE))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe expected
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }
  }

  it should "extract the namespace envelope from the attribute" in {

    val source = Source(List(ByteString(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><GovTalkMessage xmlns=\"http://www.govtalk.gov.uk/CM/envelope\"><EnvelopeVersion>2.0</EnvelopeVersion>"), ByteString(
      "<Header><MessageDetails><Class>HMRC-CT-CT600</Class><Qualifier>request</Qualifier><Function>submit"), ByteString(
      "</Function><TransactionID></TransactionID><CorrelationID>454545454</CorrelationID><Transformation>"), ByteString(
      "XML</Transformation><GatewayTest>0</GatewayTest></MessageDetails><SenderDetails><IDAuthentication>"), ByteString(
      "<SenderID>user1</SenderID><Authentication><Method>clear</Method><Role>principal</Role>"), ByteString(
      "<Value>pass</Value></Authentication></IDAuthentication></SenderDetails></Header><GovTalkDetails>"), ByteString(
      "<Keys><Key Type=\"TestKey\">Retry2</Key></Keys> <TargetDetails><Organisation>CapGemini</Organisation>"), ByteString(
      "</TargetDetails><ChannelRouting><Channel><URI>1192</URI> <Product>HMRC CT600</Product>"), ByteString(
      "<Version>1.0.1</Version></Channel></ChannelRouting></GovTalkDetails><Body></Body>"), ByteString(
      "</GovTalkMessage>")))


    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class")),
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage"),Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"),None),
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("HMRC-CT-CT600")),
      XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "897"), Some(CompleteChunkStage.STREAM_SIZE))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe expected
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }
  }


}
