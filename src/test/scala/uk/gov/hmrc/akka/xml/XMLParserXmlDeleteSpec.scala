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

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
  * Created by abhishek on 26/01/17.
  */
class XMLParserXmlDeleteSpec extends AnyFlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {

  val f = fixtures

  import f._

  behavior of "FastParsingStage#parser"
  it should "delete a element from a valid xml when xml is in single chunk" in {
    val source = Source.single(ByteString("<xml><header><id>12345</id></header></xml>"))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(FastParsingStage.STREAM_SIZE -> "42"), Some(FastParsingStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header></header></xml>"
    }
  }

  it should "delete a element from a valid xml when xml is in two chunk - text is divided" in {
    val source = Source(List(ByteString("<xml><header><id>12"), ByteString("345</id></header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(FastParsingStage.STREAM_SIZE -> "42"), Some(FastParsingStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header></header></xml>"
    }
  }

  it should "delete a element from a valid xml when xml is in two chunk - opening tag is divided" in {
    val source = Source(List(ByteString("<xml><header><i"), ByteString("d>12345</id></header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(FastParsingStage.STREAM_SIZE -> "42"), Some(FastParsingStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header></header></xml>"
    }
  }

  it should "delete a element from a valid xml when xml is in two chunk - closing tag is divided" in {
    val source = Source(List(ByteString("<xml><header><id>12345</i"), ByteString("d></header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(FastParsingStage.STREAM_SIZE -> "42"), Some(FastParsingStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header></header></xml>"
    }
  }

  it should "delete a multilevel element in multiple chunks" in {
    val source = Source(List(ByteString("<xml><header><content><foo>foo</"), ByteString("foo><bar>bar</bar></content>"),
      ByteString("</header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "content")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(FastParsingStage.STREAM_SIZE -> "75"), Some(FastParsingStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header></header></xml>"
    }
  }

  it should "ignore delete if no delete tags are found" in {
    val source = Source(List(ByteString("<xml><header><content><foo>foo</"), ByteString("foo><bar>bar</bar></content>"),
      ByteString("</header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(FastParsingStage.STREAM_SIZE -> "75"), Some(FastParsingStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><header><content><foo>foo</foo><bar>bar</bar></content></header></xml>"
    }
  }


  it should "insert an element and delete an element" in {
    val source = Source.single(ByteString("<xml><header></header><body><title>hello</title></body></xml>"))
    val upsertBlock: String => String = (prefix: String) => "bar"

    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }

  it should "insert an element and delete an element - multiple chunks" in {
    val source = Source(List(ByteString("<xml><header></header><body><ti"), ByteString("tle>hello</title></body></xml>")))
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }

  it should "donot insert a prolog in first chunk if already exists" in {
    val source = Source(List(ByteString("<?xml version=\"1.1\"?><xml><header></header><body><ti"), ByteString("tle>hello</title></body></xml>")))
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.1\"?><xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }

  it should "donot insert a prolog in first chunk if already exists even if flag is set" in {
    val source = Source(List(ByteString("<?xml version=\"1.1\"?><xml><header></header><body><ti"), ByteString("tle>hello</title></body></xml>")))
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions, true))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.1\"?><xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }

  it should "insert a prolog in first chunk" in {
    val source = Source(List(ByteString("<xml><header></header><body><ti"), ByteString("tle>hello</title></body></xml>")))
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions, true))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }

  it should "insert a prolog in first chunk before comments" in {
    val source = Source(List(ByteString("<!-- Comments --><xml><header></header><body><ti"), ByteString("tle>hello</title></body></xml>")))
    val upsertBlock: String => String = (prefix: String) => "bar"
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), upsertBlock, isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions, true))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!-- Comments --><xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }

  it should "be able to delete parts of a ByteString" in {
    val pdf = new ParsingDataFunctions {}
    val remainingBytes = pdf.deleteBytes(ByteString.fromString("1234567890"), 2, 5, 7)
    remainingBytes.utf8String shouldBe "345890"
  }

}
