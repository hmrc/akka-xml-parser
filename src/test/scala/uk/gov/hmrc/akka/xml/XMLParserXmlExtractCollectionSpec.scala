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
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar

/**
  * Created by christine on 26/04/17.
  */
class XMLParserXmlExtractCollectionSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {

  val f = fixtures

  import f._

  behavior of "ParsingStage#parser"

  it should "extract a single pair of value from a valid xml" in {
    val source = Source.single(ByteString("ï»¿<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar></body></xml>"))
    val paths = Seq[XMLInstruction](XMLExtractCollection(Seq("xml", "body"), Seq("xml", "body", "foo"), Seq("xml", "body", "bar")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "body"), Map("foo" -> "bar"), Some("")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "83"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar></body></xml>"
    }

  }

  it should "extract a more than one pair of value from a valid xml" in {
    val source = Source.single(ByteString("<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar><foo>foo1</foo><bar>bar1</bar></body></xml>"))
    val paths = Seq[XMLInstruction](XMLExtractCollection(Seq("xml", "body"), Seq("xml", "body", "foo"), Seq("xml", "body", "bar")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "body"), Map("foo" -> "bar", "foo1" -> "bar1"), Some("")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "113"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar><foo>foo1</foo><bar>bar1</bar></body></xml>"
    }

  }
  it should "not extract a single pair value when the bytes are split and element is empty" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>"),
      ByteString("</id></header><body><fo"),
        ByteString("o></foo><bar></bar></body></xml>")))
    val paths = Seq[XMLInstruction](XMLExtractCollection(Seq("xml", "body"), Seq("xml", "body", "foo"), Seq("xml", "body", "bar")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "72"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header><body><foo></foo><bar></bar></body></xml>"
    }
  }

  it should "extract pair values when the bytes are split" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>"),
      ByteString("</id></header><bo"),
        ByteString("dy><fo"),
      ByteString("o>"), ByteString("fo"), ByteString("o</fo"), ByteString("o><ba"),
      ByteString("r>ba"), ByteString("r</ba"), ByteString("r></body></xml>")))
    val paths = Seq[XMLInstruction](XMLExtractCollection(Seq("xml", "body"), Seq("xml", "body", "foo"), Seq("xml", "body", "bar")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Seq("xml", "body"), Map("foo" -> "bar"), Some("")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "78"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header><body><foo>foo</foo><bar>bar</bar></body></xml>"
    }
  }

  it should "not extract pair values when the bytes are split and element is whitespace" in {
    val source = Source(List(ByteString("<xml><header><i"),
      ByteString("d>"),
      ByteString("</id></header><bo"),
      ByteString("dy><fo"),
      ByteString("o>"), ByteString("  "), ByteString(" </fo"), ByteString("o><ba"),
      ByteString("r>ba"), ByteString("r</ba"), ByteString("r></body></xml>")))
    val paths = Seq[XMLInstruction](XMLExtractCollection(Seq("xml", "body"), Seq("xml", "body", "foo"), Seq("xml", "body", "bar")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "78"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id></id></header><body><foo>   </foo><bar>bar</bar></body></xml>"
    }
  }

  it should "handle when none of the xPaths exist" in {
    val source = Source.single(ByteString("ï»¿<xml><header><id>12345</id></header></xml>"))
    val paths = Seq[XMLInstruction](XMLExtractCollection(Seq("xml", "body"), Seq("xml", "body", "foo"), Seq("xml", "body", "bar")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "42"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header></xml>"
    }
  }

  it should "handle when the main xPath does not exist" in {
    val source = Source.single(ByteString("ï»¿<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar></body></xml>"))
    val paths = Seq[XMLInstruction](XMLExtractCollection(Seq("xml", "body1"), Seq("xml", "body", "foo"), Seq("xml", "body", "bar")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "83"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar></body></xml>"
    }
  }

  it should "handle when if the key xPath does not exist" in {
    val source = Source.single(ByteString("ï»¿<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar></body></xml>"))
    val paths = Seq[XMLInstruction](XMLExtractCollection(Seq("xml", "body"), Seq("xml", "body", "foo1"), Seq("xml", "body", "bar")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "83"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar></body></xml>"
    }
  }

  it should "handle when the value xPath does not exist" in {
    val source = Source.single(ByteString("ï»¿<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar></body></xml>"))
    val paths = Seq[XMLInstruction](XMLExtractCollection(Seq("xml", "body"), Seq("xml", "body", "foo"), Seq("xml", "body1", "bar")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "83"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id></header><body><foo>foo</foo><bar>bar</bar></body></xml>"
    }
  }


}
