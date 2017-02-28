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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import org.scalatest.time.{Millis, Seconds, Span}

/**
  * Created by abhishek on 09/12/16.
  */
class XMLParserXmlUpdateSpec
  extends FlatSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with Eventually
    with XMLParserFixtures {

  val f = fixtures
  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  import f._

  behavior of "AkkaXMLParser#parser"

  it should "update an element where there is an XMLUpsert instruction and the element exists at the expected xPath" in {
    val source = Source.single(ByteString("<xml><header><foo>foo123</foo></header></xml>"))
    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))
    val expected = "<xml><header><foo>bar</foo></header></xml>"

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update an element where there is an XMLUpsert instruction and the element is empty at the expected xPath" in {
    val source = Source.single(ByteString("<xml><header><foo></foo></header></xml>"))
    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update an element where there is a self closing start tag at the expected xPath" in {
    val source = Source.single(ByteString("<xml><header><foo/></header></xml>"))
    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update an element where it is split over multiple chunks" in {
    val source = Source(List(ByteString("<xml><header><foo>fo"), ByteString("o</foo></header></xml>")))
    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update an element where the start tag is split over multiple chunks" in {
    val source = Source(List(ByteString("<xml><header><fo"), ByteString("o>foo</foo></header></xml>")))
    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("barbar")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>barbar</foo></header></xml>"
    }

  }

  it should "update an element where the end tag is split over multiple chunks" in {
    val source = Source(List(ByteString("<xml><header><fo"), ByteString("o>foo</foo></header></xml>")))
    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update a block of xml element" in {
    val source = Source.single(ByteString("<xml><header><foo><taz>tar</taz></foo></header></xml>"))
    val instructions = Set[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), Some("<bar>bar</bar>"), isUpsert = true)
    )

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo><bar>bar</bar></foo></header></xml>"
    }
  }

  it should "update and delete a block of xml element - data is chunked" in {
    val source = Source(List(ByteString("<xml><header><fo"),
      ByteString("o><taz>tar</ta"),
      ByteString("z></foo></he"),
      ByteString("ader></xml>")))
    val instructions = Set[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), Some("<bar>bar</bar>"), isUpsert = true)
    )

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo><bar>bar</bar></foo></header></xml>"
    }
  }

  it should "update an element where multiple elements are split over multiple chunks" in {
    val source = Source(List(ByteString("<xm"), ByteString("l><heade"),
      ByteString("r><foo"), ByteString(">fo111o</fo"), ByteString("o></header"), ByteString("></xml>")))
    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))
    whenReady(source.runWith(parseToByteString(instructions))) { r =>

      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "insert an element where it does not exist and there is an upsert instruction" in {
    val source = Source.single(ByteString("<xml><header></header></xml>"))
    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar"), isUpsert = true))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "not insert an element where it does not exist and there is an update without upsert instruction" in {
    val source = Source.single(ByteString("<xml><header></header></xml>"))
    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header></header></xml>"
    }
  }
  //TODO: Check if we need to program for this scenario with Rob

  //  it should "extract the inserted value when it is updated" in {
  //    val source = Source.single(ByteString("<xml><body><foo>foo</foo></body></xml>"))
  //    val instructions = Set[XMLInstruction](
  //      XMLUpdate(Seq("xml", "body", "foo"), Some("bar")))
  //    whenReady(source.runWith(parseToXMLElements(instructions))) { r =>
  //      r shouldBe Set(XMLElement(Seq("xml", "body", "foo"), Map.empty, Some("bar")))
  //    }
  //  }
  //
  //
  //  it should "extract the inserted value when an empty tag is updated" in {
  //    val source = Source.single(ByteString("<xml><body><foo></foo></body></xml>"))
  //    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "body", "foo"), Some("bar")))
  //
  //    whenReady(source.runWith(parseToXMLElements(instructions))) { r =>
  //      r shouldBe Set(XMLElement(Seq("xml", "body", "foo"), Map.empty, Some("bar")))
  //    }
  //  }
  //
  //  it should "extract the inserted value when a self closing tag is updated" in {
  //    val source = Source.single(ByteString("<xml><body><foo/></body></xml>"))
  //    val instructions = Set[XMLInstruction](
  //      XMLUpdate(Seq("xml", "body", "foo"), Some("bar"))
  //    )
  //
  //    whenReady(source.runWith(parseToXMLElements(instructions))) { r =>
  //      r shouldBe Set(XMLElement(Seq("xml", "body", "foo"), Map.empty, Some("bar")))
  //    }
  //  }

  it should "update an existing element with new attributes when they are specified" in {
    val source = Source.single(ByteString("<xml><bar>bar</bar></xml>"))
    val instructions = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), Map("attribute" -> "value")))
    val expected = "<xml><bar attribute=\"value\">foo</bar></xml>"

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "insert an element with attributes where it does not exist" in {
    val source = Source.single(ByteString("<xml></xml>"))
    val instructions = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), Map("attribute" -> "value"), isUpsert = true))

    val expected = "<xml><bar attribute=\"value\">foo</bar></xml>"

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update with multiple attributes" in {

    val source = Source.single(ByteString("<xml><bar>bar</bar></xml>"))
    val instructions = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), Map("attribute" -> "value", "attribute2" -> "value2")))
    val expected = "<xml><bar attribute=\"value\" attribute2=\"value2\">foo</bar></xml>".getBytes

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe new String(expected)
    }
  }

  it should "insert multiple elements when root element ('one') is not present" in {
    val source = Source.single(ByteString("<xml><foo><bar>bar</bar></foo></xml>"))
    val instructions = Set[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), Some("""<two attribute="value">two</two>"""), isUpsert = true)
    )
    val expected = """<xml><foo><bar>bar</bar><one><two attribute="value">two</two></one></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update multiple elements when root element ('one') is present" in {
    val source = Source.single(ByteString("<xml><foo><bar>bar</bar><one></one></foo></xml>"))
    val instructions = Set[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), Some("""<two attribute="value">two</two>"""))
    )
    val expected = """<xml><foo><bar>bar</bar><one><two attribute="value">two</two></one></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update multiple elements when root element ('one') is an empty present" in {
    val source = Source.single(ByteString("<xml><foo><bar>bar</bar><one/></foo></xml>"))
    val instructions = Set[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), Some("""<two attribute="value">two</two>"""))
    )
    val expected = """<xml><foo><bar>bar</bar><one><two attribute="value">two</two></one></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "insert multiple elements with multiple instructions" in {
    val source = Source.single(ByteString("<xml><foo></foo></xml>"))
    val instructions = Set[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), Some("one"), isUpsert = true),
      XMLUpdate(XPath("xml/foo/two"), Some("two"), isUpsert = true)
    )
    val expected = """<xml><foo><one>one</one><two>two</two></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }


  it should "update multiple elements with multiple instructions" in {
    val source = Source.single(ByteString("<xml><foo><one></one><two></two></foo></xml>"))
    val instructions = Set[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), Some("one")),
      XMLUpdate(XPath("xml/foo/two"), Some("two"))
    )
    val expected = """<xml><foo><one>one</one><two>two</two></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>

      r.utf8String shouldBe expected
    }
  }

  it should "insert elements with namespaces" in {
    val source = Source.single(ByteString("""<ns:xml xmlns:ns="test"></ns:xml>"""))
    val instructions = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), isUpsert = true))
    val expected = "<ns:xml xmlns:ns=\"test\"><ns:bar>foo</ns:bar></ns:xml>"

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update an elements with namespaces and input in chunks" in {
    val source = Source(List(
      ByteString("""<ns:xml xml"""),
      ByteString("""ns:ns="test"><ns:bar/></ns:xml>""")))
    val instructions = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), isUpsert = true))
    val expected = "<ns:xml xmlns:ns=\"test\"><ns:bar>foo</ns:bar></ns:xml>"
    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "insert elements with namespaces and input in chunks" in {
    val source = Source(List(
      ByteString("""<ns:xml xml"""),
      ByteString("""ns:ns="test"></ns:xml>""")))
    val instructions = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), isUpsert = true))
    val expected = "<ns:xml xmlns:ns=\"test\"><ns:bar>foo</ns:bar></ns:xml>"
    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "test" in {
    val source = Source(List(
      ByteString(
        """<?xml version='1.0' encoding='UTF-8'?>
                   <GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope">
                   <EnvelopeVersion>2.0</EnvelopeVersion>
                   <Header>
                   <MessageDetails>
                   <Class>HMRC-PAYE-RTI-EAS-TIL</Class>
                   <Qualifier>error</Qualifier>
                   <Function>submit</Function>
                   <CorrelationID>4FA7B03FA8214BC59801008DF5378C92</Correlati"""),
      ByteString(
        """onID>
                   <ResponseEndPoint></ResponseEndPoint>
                   <Transformation>XML</Transformation>
                   <GatewayTimestamp/>
                   </MessageDetails>
                   <SenderDetails></SenderDetails>
                   </Header>
                   <GovTalkDetails>
                   <Keys></Keys>
                   <GovTalkErrors>
                   <Error>
                   <RaisedBy>Department</RaisedBy>
                   <Number>3001</Number>
                   <Type>business</Type>
                   <Text>The submission of this document has failed due to departmental specific business logic in the Body tag.</Text>
                   </Error>
                   </GovTalkErrors>
                   </GovTalkDetails>
                   <Body>
                   <ErrorResponse SchemaVersion="2.0" xmlns="http://www.govtalk.gov.uk/CM/errorresponse">
                   <Application>
                   <MessageCount>1</MessageCount>
                   </Application>
                   <Error>
                   <RaisedBy>ChRIS</RaisedBy>
                   <Number>7811</Number>
                   <Type>business</Type>
                   <Text>This submission cannot be accepted as alignment has commenced or successfully completed1111</Text>
                   <Location></Location>
                   </Error>
                   </ErrorResponse>
                   </Body>
                   </GovTalkMessage>""")))
    val instructions = Set[XMLInstruction](
      XMLUpdate(XPath("GovTalkMessage/Header/MessageDetails/GatewayTimestamp"), Some("2017-02-27"), isUpsert = true),
      XMLUpdate(XPath("GovTalkMessage/Header/MessageDetails/ResponseEndPoint"), Some("www.google.com"), isUpsert = true),
      XMLUpdate(XPath("GovTalkMessage/GovTalkDetails/GovTalkErrors/Error/RaisedBy"), Some("Department"), isUpsert = true),
      XMLUpdate(XPath("GovTalkMessage/GovTalkDetails/GovTalkErrors/Error/Text"), Some("The submission of this document has " +
        "failed due to departmental specific business logic in the Body tag."), isUpsert = true)
    )
    val expected = "<ns:xml xmlns:ns=\"test\"><ns:bar>foo</ns:bar></ns:xml>"
    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      println(r.utf8String)
      //r.utf8String shouldBe expected
    }
  }


}
