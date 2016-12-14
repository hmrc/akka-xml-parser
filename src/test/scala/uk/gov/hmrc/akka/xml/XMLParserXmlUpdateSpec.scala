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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}

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

  import f._

  behavior of "AkkaXMLParser#parser"

  it should "update an element where there is an XMLUpsert instruction and the element exists at the expected xPath" in {
    val source = Source.single(ByteString("<xml><header><foo>foo123</foo></header></xml>"))
    val paths = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    val expected = "<xml><header><foo>bar</foo></header></xml>"

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      println(r.utf8String)
      println(expected)
      r.utf8String shouldBe expected
    }
  }

  it should "update an element where there is an XMLUpsert instruction and the element is empty at the expected xPath" in {
    val source = Source.single(ByteString("<xml><header><foo></foo></header></xml>"))
    val paths = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update an element where there is a self closing start tag at the expected xPath" in {
    val source = Source.single(ByteString("<xml><header><foo/></header></xml>"))

    val paths = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "update an element where it is split over multiple chunks" in {
    val source = Source(List(ByteString("<xml><header><foo>fo"), ByteString("o</foo></header></xml>")))

    val paths = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }


  //  it should "update an element where the start tag is split over multiple chunks" in {
  //    implicit val reader = getReader
  //    val enum = Enumerator("<xml><header><fo".getBytes, "o>foo</foo></header></xml>".getBytes)
  //
  //    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))
  //
  //    val parser = XMLParser.getXMLParser(instructions)
  //
  //    val res = enum &> parser &> Enumeratee.map(_._1) |>>> Iteratee.consume[Array[Byte]]()
  //
  //    whenReady(res) { r =>
  //      new String(r) shouldBe "<xml><header><foo>bar</foo></header></xml>"
  //    }
  //
  //    reader.close()
  //  }
  //
  //  it should "update an element where the end tag is split over multiple chunks" in {
  //    implicit val reader = getReader
  //    val enum = Enumerator("<xml><header><foo>foo</fo".getBytes, "o></header></xml>".getBytes)
  //
  //    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))
  //
  //    val parser = XMLParser.getXMLParser(instructions)
  //
  //    val res = enum &> parser &> Enumeratee.map(_._1) |>>> Iteratee.consume[Array[Byte]]()
  //
  //    whenReady(res) { r =>
  //      new String(r) shouldBe "<xml><header><foo>bar</foo></header></xml>"
  //    }
  //
  //    reader.close()
  //  }
  //  it should "update an element where multiple elements are split over multiple chunks" in {
  //    implicit val reader = getReader
  //    val enum = Enumerator("<xm".getBytes, "l><heade".getBytes, "r><foo".getBytes, ">foo</fo".getBytes, "o></header".getBytes, "></xml>".getBytes)
  //
  //    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))
  //
  //    val parser = XMLParser.getXMLParser(instructions)
  //
  //    val res = enum &> parser &> Enumeratee.map(_._1) |>>> Iteratee.consume[Array[Byte]]()
  //
  //    whenReady(res) { r =>
  //      new String(r) shouldBe "<xml><header><foo>bar</foo></header></xml>"
  //    }
  //
  //    reader.close()
  //  }
  //
  //  it should "insert an element where it does not exist and there is an upsert instruction" in {
  //    implicit val reader = getReader
  //    val enum = Enumerator("<xml><header></header></xml>".getBytes)
  //
  //    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar"), isUpsert = true))
  //
  //    val parser = XMLParser.getXMLParser(instructions)
  //
  //    val res = enum &> parser &> Enumeratee.map(_._1) |>>> Iteratee.consume[Array[Byte]]()
  //
  //    whenReady(res) { r =>
  //      new String(r) shouldBe "<xml><header><foo>bar</foo></header></xml>"
  //    }
  //
  //    reader.close()
  //  }
  //
  //  it should "not insert an element where it does not exist and there is an update without upsert instruction" in {
  //    implicit val reader = getReader
  //    val enum = Enumerator("<xml><header></header></xml>".getBytes)
  //
  //    val instructions = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))
  //
  //    val parser = XMLParser.getXMLParser(instructions)
  //
  //    val res = enum &> parser &> Enumeratee.map(_._1) |>>> Iteratee.consume[Array[Byte]]()
  //
  //    whenReady(res) { r =>
  //      new String(r) shouldBe "<xml><header></header></xml>"
  //    }
  //
  //    reader.close()
  //  }
  //
  //  it should "extract the inserted value when it is updated" in {
  //    implicit val reader = getReader
  //    val enum = Enumerator("<xml><body><foo>foo</foo></body></xml>".getBytes)
  //
  //    val paths = Set[XMLInstruction](XMLUpdate(Seq("xml", "body", "foo"), Some("bar")))
  //
  //    val parser = XMLParser.getXMLParser(paths)
  //
  //    val res = enum &> parser &> Enumeratee.map(_._2) |>>> mapFoldingIteratee
  //
  //    whenReady(res) { r =>
  //      removeCount(r) shouldBe Set(XMLElement(Seq("xml", "body", "foo"), Map.empty, Some("bar")))
  //    }
  //
  //    reader.close()
  //  }
  //
  //  it should "extract the inserted value when an empty tag is updated" in {
  //    implicit val reader = getReader
  //    val enum = Enumerator("<xml><body><foo></foo></body></xml>".getBytes)
  //
  //    val paths = Set[XMLInstruction](XMLUpdate(Seq("xml", "body", "foo"), Some("bar")))
  //
  //    val parser = XMLParser.getXMLParser(paths)
  //
  //    val res = enum &> parser &> Enumeratee.map(_._2) |>>> mapFoldingIteratee
  //
  //    whenReady(res) { r =>
  //      removeCount(r) shouldBe Set(XMLElement(Seq("xml", "body", "foo"), Map.empty, Some("bar")))
  //    }
  //
  //    reader.close()
  //  }

  //  it should "extract the inserted value when a self closing tag is updated" in {
  //    val source = Source.single(ByteString("<xml><body><foo/></body></xml>"))
  //    val paths = Set[XMLInstruction](
  //      XMLUpdate(Seq("xml", "body", "foo"), Some("bar"))
  //    )
  //
  //    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
  //      r shouldBe Set(XMLElement(Seq("xml", "body", "foo"), Map.empty, Some("bar")))
  //    }
  //  }

  it should "update an existing element with new attributes when they are specified" in {
    val source = Source.single(ByteString("<xml><bar>bar</bar></xml>"))
    val paths = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), Map("attribute" -> "value")))
    val expected = "<xml><bar attribute=\"value\">foo</bar></xml>"

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe expected
    }
  }



  it should "insert an element with attributes where it does not exist" in {
    val source = Source.single(ByteString("<xml></xml>"))
    val paths = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), Map("attribute" -> "value"), isUpsert = true))

    val expected = "<xml><bar attribute=\"value\">foo</bar></xml>"

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe expected
    }
  }

  it should "update with multiple attributes" in {

    val source = Source.single(ByteString("<xml><bar>bar</bar></xml>"))
    val paths = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), Map("attribute" -> "value", "attribute2" -> "value2")))
    val expected = "<xml><bar attribute=\"value\" attribute2=\"value2\">foo</bar></xml>".getBytes

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe new String(expected)
    }
  }

    it should "insert multiple elements even if they rely on each other" in {
      val source = Source.single(ByteString("<xml><foo><bar>bar</bar></foo></xml>"))
      val paths = Set[XMLInstruction](
        XMLUpdate(XPath("xml/foo/one"), isUpsert = true),
        XMLUpdate(XPath("xml/foo/one/two"), Some("two"), Map("attribute" -> "value"), isUpsert = true)
      )
      val expected = "<xml><foo><bar>bar</bar><one><two attribute=\"value\">two</two></one></foo></xml>"

      whenReady(source.runWith(parseToByteString(paths))) { r =>
        println(r.utf8String)
        println(expected)
        r.utf8String shouldBe expected
      }
    }

  it should "insert elements with namespaces" in {
    val source = Source.single(ByteString("""<ns:xml xmlns:ns="test"></ns:xml>"""))
    val paths = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), isUpsert = true))
    val expected = "<ns:xml xmlns:ns=\"test\"><ns:bar>foo</ns:bar></ns:xml>"

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      println(r.utf8String)
      println(expected)
      r.utf8String shouldBe expected
    }
  }

  it should "insert elements with namespaces and input in chunks" in {
    val source = Source(List(
      ByteString("""<ns:xml xml"""),
      ByteString("""ns:ns="test"></ns:xml>""")))
    val paths = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), isUpsert = true))
    val expected = "<ns:xml xmlns:ns=\"test\"><ns:bar>foo</ns:bar></ns:xml>"
//    whenReady(source.runWith(parseToPrint(paths))) { r =>
//    }


        whenReady(source.runWith(parseToByteString(paths))) { r =>
          println()
          println(r.utf8String)
          println(expected)
          r.utf8String shouldBe expected
        }
  }
}
