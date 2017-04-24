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
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), upsertBlock))
    val expected = "<xml><header><foo>bar</foo></header></xml>"

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update an element where there is an XMLUpsert instruction and the element is empty at the expected xPath" in {
    val source = Source.single(ByteString("<xml><header><foo></foo></header></xml>"))
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), upsertBlock))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update an element where there is a self closing start tag at the expected xPath" in {
    val source = Source.single(ByteString("<xml><header><foo/></header></xml>"))
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), upsertBlock))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update an element where it is split over multiple chunks" in {
    val source = Source(List(ByteString("<xml><header><foo>fo"), ByteString("o</foo></header></xml>")))
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), upsertBlock))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update an element where the start tag is split over multiple chunks" in {
    val source = Source(List(ByteString("<xml><header><fo"), ByteString("o>foo</foo></header></xml>")))
    val upsertBlock: String => String = (prefix: String) => "barbar"
    val instructions = Seq[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), upsertBlock))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>barbar</foo></header></xml>"
    }

  }

  it should "update an element where the end tag is split over multiple chunks" in {
    val source = Source(List(ByteString("<xml><header><fo"), ByteString("o>foo</foo></header></xml>")))
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), upsertBlock))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update a block of xml element" in {
    val source = Source.single(ByteString("<xml><header><foo><taz>tar</taz></foo></header></xml>"))
    val upsertBlock: String => String = (prefix: String) => "<bar>bar</bar>"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true)
    )

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo><bar>bar</bar></foo></header></xml>"
    }
  }

  it should "update a block of xml element with prefix" in {
    val source = Source.single(ByteString("""<h:xml xmlns:h="test"><h:header><h:foo><h:taz>tar</h:taz></h:foo></h:header></h:xml>"""))
    val upsertBlock: String => String = (prefix: String) => s"<$prefix:bar>bar</$prefix:bar>"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true)
    )

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<h:xml xmlns:h=\"test\"><h:header><h:foo><h:bar>bar</h:bar></h:foo></h:header></h:xml>"
    }
  }

  it should "update a multi element block of xml element with prefix" in {
    val source = Source.single(ByteString("""<h:xml xmlns:h="test"><h:header><h:foo><h:taz>tar</h:taz><h:taz>tar</h:taz><h:taz>tar</h:taz></h:foo></h:header></h:xml>"""))
    val upsertBlock: String => String = (prefix: String) => s"<$prefix:bar>bar</$prefix:bar>"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true)
    )

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<h:xml xmlns:h=\"test\"><h:header><h:foo><h:bar>bar</h:bar></h:foo></h:header></h:xml>"
    }
  }


  it should "update and delete a block of xml element - data is chunked" in {
    val upsertBlock: String => String = (prefix: String) => "<bar>bar</bar>"
    val source = Source(List(ByteString("<xml><header><fo"),
      ByteString("o><taz>tar</ta"),
      ByteString("z></foo></he"),
      ByteString("ader></xml>")))
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true)
    )

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo><bar>bar</bar></foo></header></xml>"
    }
  }

  it should "update an element where multiple elements are split over multiple chunks" in {
    val upsertBlock: String => String = (prefix: String) => "bar"
    val source = Source(List(ByteString("<xm"), ByteString("l><heade"),
      ByteString("r><foo"), ByteString(">fo111o</fo"), ByteString("o></header"), ByteString("></xml>")))
    val instructions = Seq[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), upsertBlock))
    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "insert an element where it does not exist and there is an upsert instruction" in {
    val upsertBlock: String => String = (prefix: String) => "bar"
    val source = Source.single(ByteString("<xml><header></header></xml>"))
    val instructions = Seq[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "not insert an element where it does not exist and there is an update without upsert instruction" in {
    val upsertBlock: String => String = (prefix: String) => "bar"
    val source = Source.single(ByteString("<xml><header></header></xml>"))
    val instructions = Seq[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), upsertBlock))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header></header></xml>"
    }
  }


  it should "update an existing element with new attributes when they are specified" in {
    val source = Source.single(ByteString("<xml><bar>bar</bar></xml>"))
    val upsertBlock: String => String = (prefix: String) => "foo"
    val instructions = Seq[XMLInstruction](XMLUpdate(XPath("xml/bar"), upsertBlock, Map("attribute" -> "value")))
    val expected = "<xml><bar attribute=\"value\">foo</bar></xml>"

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "insert an element with attributes where it does not exist" in {
    val source = Source.single(ByteString("<xml></xml>"))
    val upsertBlock: String => String = (prefix: String) => "foo"
    val instructions = Seq[XMLInstruction](XMLUpdate(XPath("xml/bar"), upsertBlock, Map("attribute" -> "value"), isUpsert = true))

    val expected = "<xml><bar attribute=\"value\">foo</bar></xml>"

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update with multiple attributes" in {

    val source = Source.single(ByteString("<xml><bar>bar</bar></xml>"))
    val upsertBlock: String => String = (prefix: String) => "foo"
    val instructions = Seq[XMLInstruction](XMLUpdate(XPath("xml/bar"), upsertBlock, Map("attribute" -> "value", "attribute2" -> "value2")))
    val expected = "<xml><bar attribute=\"value\" attribute2=\"value2\">foo</bar></xml>".getBytes

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe new String(expected)
    }
  }

  it should "insert multiple elements when root element ('one') is not present" in {
    val source = Source.single(ByteString("<xml><foo><bar>bar</bar></foo></xml>"))
    val upsertBlock: String => String = (prefix: String) => """<two attribute="value">two</two>"""
    val instructions = Seq[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), upsertBlock, isUpsert = true)
    )
    val expected = """<xml><foo><bar>bar</bar><one><two attribute="value">two</two></one></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update multiple elements when root element ('one') is present" in {
    val source = Source.single(ByteString("<xml><foo><bar>bar</bar><one></one></foo></xml>"))
    val upsertBlock: String => String = (prefix: String) => """<two attribute="value">two</two>"""
    val instructions = Seq[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), upsertBlock)
    )
    val expected = """<xml><foo><bar>bar</bar><one><two attribute="value">two</two></one></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update multiple elements when root element ('one') is an empty present" in {
    val source = Source.single(ByteString("<xml><foo><bar>bar</bar><one/></foo></xml>"))
    val upsertBlock: String => String = (prefix: String) => """<two attribute="value">two</two>"""
    val instructions = Seq[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), upsertBlock)
    )
    val expected = """<xml><foo><bar>bar</bar><one><two attribute="value">two</two></one></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "insert multiple elements with multiple instructions" in {
    val source = Source.single(ByteString("<xml><foo></foo></xml>"))
    val upsertBlock1: String => String = (prefix: String) => "one"
    val upsertBlock2: String => String = (prefix: String) => "two"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), upsertBlock1, isUpsert = true),
      XMLUpdate(XPath("xml/foo/two"), upsertBlock2, isUpsert = true)
    )
    val expected = """<xml><foo><one>one</one><two>two</two></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }


  it should "update multiple elements with multiple instructions" in {
    val source = Source.single(ByteString("<xml><foo><one></one><two></two></foo></xml>"))
    val upsertBlock1: String => String = (prefix: String) => "one"
    val upsertBlock2: String => String = (prefix: String) => "two"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(XPath("xml/foo/one"), upsertBlock1),
      XMLUpdate(XPath("xml/foo/two"), upsertBlock2)
    )
    val expected = """<xml><foo><one>one</one><two>two</two></foo></xml>"""

    whenReady(source.runWith(parseToByteString(instructions))) { r =>

      println(r.utf8String)
      r.utf8String shouldBe expected
    }
  }

  it should "insert elements with namespaces" in {
    val source = Source.single(ByteString("""<ns:xml xmlns:ns="test"></ns:xml>"""))
    val upsertBlock: String => String = (prefix: String) => "foo"
    val instructions = Seq[XMLInstruction](XMLUpdate(XPath("xml/bar"), upsertBlock, isUpsert = true))
    val expected = "<ns:xml xmlns:ns=\"test\"><ns:bar>foo</ns:bar></ns:xml>"

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update an elements with namespaces and input in chunks" in {
    val source = Source(List(
      ByteString("""<ns:xml xml"""),
      ByteString("""ns:ns="test"><ns:bar/></ns:xml>""")))
    val upsertBlock: String => String = (prefix: String) => "foo"
    val instructions = Seq[XMLInstruction](XMLUpdate(XPath("xml/bar"), upsertBlock, isUpsert = true))
    val expected = "<ns:xml xmlns:ns=\"test\"><ns:bar>foo</ns:bar></ns:xml>"
    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "insert elements with namespaces and input in chunks" in {
    val source = Source(List(
      ByteString("""<ns:xml xml"""),
      ByteString("""ns:ns="test"></ns:xml>""")))
    val upsertBlock: String => String = (prefix: String) => "foo"
    val instructions = Seq[XMLInstruction](XMLUpdate(XPath("xml/bar"), upsertBlock, isUpsert = true))
    val expected = "<ns:xml xmlns:ns=\"test\"><ns:bar>foo</ns:bar></ns:xml>"
    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe expected
    }
  }


}