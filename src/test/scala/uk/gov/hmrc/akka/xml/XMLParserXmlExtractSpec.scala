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

  behavior of "AkkaXMLParser#parser"

  it should "extract a single value from a valid xml" in {
    val source = Source.single(ByteString("<xml><header><id>12345</id></header></xml>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")))
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header></xml>"
    }
  }

  it should "extract a single value when the bytes are split" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>12"),
      ByteString("3"),
      ByteString("45</id></header></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")))
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header></xml>"
    }
  }

  it should "extract a single value when the bytes are split and element is empty" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>"),
      ByteString("</id></header></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml", "header", "id"), Map.empty, Some("")))
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header></xml>"
    }
  }

  it should "extract a single value when the bytes are split and element is whitespace" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>  "),
      ByteString("  </id></header></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml", "header", "id"), Map.empty, Some("")))
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
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id><name>Hello</name></header></xml>"
    }
  }


  it should "extract the ID when the bytes are split and there are other elements at the same level" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>12"),
      ByteString("3"),
      ByteString("45</id><name>"),
      ByteString("He"),
      ByteString("llo</name></header></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id><name>Hello</name></header></xml>"
    }
  }


  it should "handle a malformed xml with no available metadata" in {
    val source = Source.single(ByteString("malformed"))

    whenReady(source.runWith(parseToXMLElements(Set.empty))) { r =>
      r shouldBe Set(XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS)))
    }

    whenReady(source.runWith(parseToByteString(Set.empty))) { r =>
      r.utf8String shouldBe "malformed"
    }
  }

  it should "return any already extracted metadata on a malformed xml" in {
    val source = Source.single(ByteString("<xml><header><id>12345</id></xml>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")),
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS)))
    }

    whenReady(source.runWith(parseToByteString(Set.empty))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></xml>"
    }
  }


  it should "extract available metadata if the xml is malformed after the first chunk" in {
    val source = Source(List(ByteString("<xml><header><id>12345</id>"), ByteString("</xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "header", "id"), Map.empty, Some("12345")),
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></xml>"
    }
  }

  it should "return a malformed status if an error occurs in the middle of a chunk, leaving unprocessed bytes" in {
    val source = Source(List(ByteString("<header>brokenID</brokenTag><moreBytes/>"), ByteString("</header>")))
    val paths = Set.empty[XMLInstruction]

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<header>brokenID</brokenTag><moreBytes/></header>"
    }
  }

  it should "extract attributes where the xPath is given" in {
    val source = Source.single(ByteString("<xml><body><element Attribute=\"Test\">elementText</element></body></xml>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "body", "element"), Map("Attribute" -> "Test")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
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
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml"), Map("Attribute2" -> "Test2")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("Attribute2" -> "Test2"), Some(""))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml Attribute=\"Test\" Attribute2=\"Test2\"></xml>"
    }
  }

  it should "not extract attributes where the xPath matches but the attributes differ" in {
    val source = Source.single(ByteString("<xml><body><element Attribute=\"notTest\">elementText</element></body></xml>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml", "header", "id"), Map("Attribute" -> "notTest")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set.empty
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><element Attribute=\"notTest\">elementText</element></body></xml>"
    }
  }

  it should "extract an element with no characters" in {
    val source = Source(List(ByteString("<xml type=\"test\"><bo"), ByteString("dy><foo>test</fo"),
      ByteString("o><bar>test</bar></body></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml"), Map("type" -> "test")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml"), Map("type" -> "test"), Some("")))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml type=\"test\"><body><foo>test</foo><bar>test</bar></body></xml>"
    }
  }

  it should "extract only the element with an xmlns attribute when the xmlns namespace is supplied" in {
    val source = Source(List(ByteString("<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\"><bo"),
      ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\"><body><foo>test</foo><bar>test</bar></body></xml>"
    }
  }

  it should "honour any prefixed namespace if the basic one is not required" in {
    val source = Source.single(ByteString("<gt:GovTalkMessage xmlns:gt=\"http://www.govtalk.gov.uk/CM/envelope\"><gt:EnvelopeVersion>2.0</gt:EnvelopeVersion></gt:GovTalkMessage>"))
    val paths = Set[XMLInstruction](XMLExtract(Seq("GovTalkMessage"), Map("xmlns:gt" -> "http://www.govtalk.gov.uk/CM/envelope")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(Seq("GovTalkMessage"), Map("xmlns:gt" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<gt:GovTalkMessage xmlns:gt=\"http://www.govtalk.gov.uk/CM/envelope\"><gt:EnvelopeVersion>2.0</gt:EnvelopeVersion></gt:GovTalkMessage>"
    }
  }

  it should "extract the xmlns element when other namespaces exist" in {
    val source = Source(List(ByteString("<xml xmlns=\"http://www.govtalk.gov.uk/CM/envelope\" xsi:schemaLocation=\"http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><bo"),
      ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val paths = Set[XMLInstruction](XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), Some(""))
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
    val paths = Set[XMLInstruction](
      XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")),
      XMLExtract(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")),
        XMLElement(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"), Some(""))
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
    val paths = Set[XMLInstruction](
      XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")),
      XMLExtract(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")),
        XMLElement(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"), Some(""))
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
    val paths = Set[XMLInstruction](
      XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"))
      // We deliberately exclude one extract - XMLExtract(Seq("xml"), Map("xsi:schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"))
      //XMLExtract(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), Some(""))
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
    val paths = Set[XMLInstruction](
      XMLExtract(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope")),
      XMLExtract(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"))
      //XMLExtract(Seq("xml"), Map("xmlns:xsi" -> "http://www.w3.org/2001/XMLSchema-instance"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")),
        XMLElement(Seq("xml"), Map("schemaLocation" -> "http://www.govtalk.gov.uk/CM/envelope envelope-v2-0-HMRC.xsd"), Some(""))
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


    val paths = Set[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns" -> "http://www.govtalk")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Qualifier")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Function")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "CorrelationID")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "TransactionID"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("HMRC-CT-CT600")),
      XMLElement(List("GovTalkMessage"), Map("xmlns" -> "http://www.govtalk"), Some("")),
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

    val paths = Set[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some(""))
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


    val paths = Set[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("HMRC-CT-CT600"))
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


    val paths = Set[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class")),
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns" -> "http://www.govtalk"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("HMRC-CT-CT600"))
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
    val paths = Set[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "GovTalkDetails", "Keys", "Key"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "GovTalkDetails", "Keys", "Key"), Map("Type" -> "TestKey"), Some("Retry2")),
      XMLElement(List("GovTalkMessage", "GovTalkDetails", "Keys", "Key"), Map("Type" -> "TestKey5"), Some("Retry5"))
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
//
//  it should "tx" in {
//
//    val validatingFunction = (string: String) => None
//    val source = Source.single(ByteString(
//      """
//        <GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope">
//            <EnvelopeVersion>2.0</EnvelopeVersion>
//            <Header>
//                <MessageDetails>
//                    <Class>HMRC-PSA-ACCT-TAX</Class>
//                    <Qualifier>request</Qualifier>
//                    <Function>submit</Function>
//
//
//                    <CorrelationID></CorrelationID>
//
//
//                    <Transformation>XML</Transformation>
//
//
//
//                </MessageDetails>
//                <SenderDetails>
//                    <IDAuthentication>
//                        <SenderID>user1</SenderID>
//                        <Authentication>
//                            <Method>clear</Method>
//                            <Role>Authenticate/Validate</Role>
//                            <Value>pass</Value>
//                        </Authentication>
//                    </IDAuthentication>
//
//                </SenderDetails>
//            </Header>
//            <GovTalkDetails>
//               <Keys>
//                    <Key Type="PSAID">A1234567</Key>
//
//
//            </Keys>
//                <ChannelRouting>
//                    <Channel>
//                        <URI>1352</URI>
//                        <Product>ASPIRE HMRC-VAT100-DEC</Product>
//                        <Version>1.0</Version>
//                    </Channel>
//                </ChannelRouting>
//            </GovTalkDetails>
//            <Body>
//                <IRenvelope xmlns="http://www.govtalk.gov.uk/taxation/vat/vatdeclaration/2">
//                    <IRheader>
//                        <Keys>
//                            <Key Type="VATRegNo">999989291</Key>
//                        </Keys>
//                        <PeriodID>2009-12</PeriodID>
//                        <PeriodStart>2009-10-01</PeriodStart>
//                        <PeriodEnd>2009-12-31</PeriodEnd>
//                        <IRmark Type="generic">GCxuuBCQsKaqKPk42Xgl6dPVg/M=</IRmark>
//                        <Sender>Individual</Sender>
//                    </IRheader>
//                    <VATDeclarationRequest>
//                        <VATDueOnOutputs>100.00</VATDueOnOutputs>
//                        <VATDueOnECAcquisitions>100.00</VATDueOnECAcquisitions>
//                        <TotalVAT>200.00</TotalVAT>
//                        <VATReclaimedOnInputs>10.00</VATReclaimedOnInputs>
//                        <NetVAT>190.00</NetVAT>
//                        <NetSalesAndOutputs>1000</NetSalesAndOutputs>
//                        <NetPurchasesAndInputs>1000</NetPurchasesAndInputs>
//                        <NetECSupplies>1000</NetECSupplies>
//                        <NetECAcquisitions>1000</NetECAcquisitions>
//
//                    </VATDeclarationRequest>
//                </IRenvelope>
//            </Body>
//        </GovTalkMessage>
//      """.stripMargin))
//    val paths = Set[XMLInstruction](
//      XMLExtract(Seq(GOV_TALK_MESSAGE, ENVELOPE_VERSION)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, MESSAGE_CLASS)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, QUALIFIER)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, FUNCTION)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, TRANSACTION_ID)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, CORRELATION_ID)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, RESPONSE_ENDPOINT)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, TRANSFORMATION)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, GATEWAY_TEST)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, GATEWAY_TIMESTAMP)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, SENDER_DETAILS, ID_AUTHENTICATION, SENDER_ID)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, SENDER_DETAILS, ID_AUTHENTICATION, AUTHENTICATION, PASSWORD)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, GOV_TALK_DETAILS, KEYS, KEY)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, GOV_TALK_DETAILS, CHANNEL_ROUTING, CHANNEL, CHANNEL_URI)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, GOV_TALK_DETAILS, CHANNEL_ROUTING, CHANNEL, PRODUCT)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, GOV_TALK_DETAILS, CHANNEL_ROUTING, CHANNEL, PRODUCT_VERSION)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, INCLUDE_IDENTIFIERS)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, START_DATE)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, START_TIME)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, END_DATE)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, END_TIME)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, INCLUDE_IDENTIFIERS)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, START_DATE)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, START_TIME)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, END_DATE)),
//      XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, END_TIME)),
//      XMLValidate(Seq("GovTalkMessage"), Seq("GovTalkMessage", "GovTalkDetails"), validatingFunction)
//    )
//    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
//
//      r.foreach(println(_))
//
//
//      r shouldBe Set(XMLElement(Seq("GovTalkMessage", "Header", "SenderDetails", "IDAuthentication", "Authentication", "Value")
//        , Map.empty, Some("pass")))
//    }
//
//  }

  val GOV_TALK_MESSAGE = "GovTalkMessage"
  val ENVELOPE_VERSION = "EnvelopeVersion"
  val HEADER = "Header"
  val MESSAGE_CLASS = "Class"
  val QUALIFIER = "Qualifier"
  val FUNCTION = "Function"
  val TRANSACTION_ID = "TransactionID"
  val CORRELATION_ID = "CorrelationID"
  val RESPONSE_ENDPOINT = "ResponseEndPoint"
  val POLL_INTERVAL = "PollInterval"
  val TRANSFORMATION = "Transformation"
  val GATEWAY_TEST = "GatewayTest"
  val GATEWAY_TIMESTAMP = "GatewayTimestamp"
  val SENDER_ID = "SenderID"
  val PASSWORD = "Value"
  val INCLUDE_IDENTIFIERS = "IncludeIdentifiers"
  val START_DATE = "StartDate"
  val START_TIME = "StartTime"
  val END_DATE = "EndDate"
  val END_TIME = "EndTime"
  val GEN_CORR_ID = "GenCorrID"
  val GEN_TIMESTAMP = "GenTimestamp"

  val MESSAGE_DETAILS = "MessageDetails"
  val GOV_TALK_DETAILS = "GovTalkDetails"

  val GOV_TALK_ERRORS = "GovTalkErrors"
  val ERROR = "Error"
  val RAISED_BY = "RaisedBy"
  val ERROR_RESPONSE_DEPARTMENT = "Department"
  val ERROR_RESPONSE_TEXT = "The submission of this document has failed due to departmental specific business logic in the Body tag."
  val TEXT = "Text"

  val SENDER_DETAILS = "SenderDetails"
  val ID_AUTHENTICATION = "IDAuthentication"
  val AUTHENTICATION = "Authentication"

  val KEYS = "Keys"
  val KEY = "Key"
  val BODY = "Body"
  val STATUS_REQUEST = "StatusRequest"

  val CHANNEL_ROUTING: String = "ChannelRouting"
  val CHANNEL: String = "Channel"
  val CHANNEL_URI: String = "URI"
  val PRODUCT: String = "Product"
  val PRODUCT_VERSION: String = "Version"

}
