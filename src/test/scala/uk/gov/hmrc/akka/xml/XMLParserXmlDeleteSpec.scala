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
  * Created by abhishek on 26/01/17.
  */
class XMLParserXmlDeleteSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {

  val f = fixtures

  import f._

  behavior of "CompleteChunkStage#parser"

  it should "delete a element from a valid xml when xml is in single chunk" in {
    val source = Source.single(ByteString("<xml><header><id>12345</id></header></xml>"))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "63"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><xml><header></header></xml>"
    }
  }

  it should "delete a element from a valid xml when xml is in two chunk - text is divided" in {
    val source = Source(List(ByteString("<xml><header><id>12"), ByteString("345</id></header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "63"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><xml><header></header></xml>"
    }
  }

  it should "delete a element from a valid xml when xml is in two chunk - opening tag is divided" in {
    val source = Source(List(ByteString("<xml><header><i"), ByteString("d>12345</id></header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "63"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><xml><header></header></xml>"
    }
  }

  it should "delete a element from a valid xml when xml is in two chunk - closing tag is divided" in {
    val source = Source(List(ByteString("<xml><header><id>12345</i"), ByteString("d></header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "63"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><xml><header></header></xml>"
    }
  }

  it should "delete a multilevel element in multiple chunks" in {
    val source = Source(List(ByteString("<xml><header><content><foo>foo</"), ByteString("foo><bar>bar</bar></content>"),
      ByteString("</header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "content")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "96"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><xml><header></header></xml>"
    }
  }

  it should "ignore delete if no delete tags are found" in {
    val source = Source(List(ByteString("<xml><header><content><foo>foo</"), ByteString("foo><bar>bar</bar></content>"),
      ByteString("</header></xml>")))
    val paths = Seq[XMLInstruction](XMLDelete(Seq("xml", "header", "id")))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "96"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><xml><header><content><foo>foo</foo><bar>bar</bar></content></header></xml>"
    }
  }


  it should "insert an element and delete an element" in {
    val source = Source.single(ByteString("<xml><header></header><body><title>hello</title></body></xml>"))
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), Some("bar"), isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }

  it should "insert an element and delete an element - multiple chunks" in {
    val source = Source(List(ByteString("<xml><header></header><body><ti"), ByteString("tle>hello</title></body></xml>")))
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), Some("bar"), isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }

  it should "insert a prolog in first chunk" in {
    val source = Source(List(ByteString("<xml><header></header><body><ti"), ByteString("tle>hello</title></body></xml>")))
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), Some("bar"), isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }

  it should "donot insert a prolog in first chunk if already exists" in {
    val source = Source(List(ByteString("<?xml version=\"1.1\"?><xml><header></header><body><ti"), ByteString("tle>hello</title></body></xml>")))
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), Some("bar"), isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.1\"?><xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }


  it should "insert a prolog in first chunk before comments" in {
    val source = Source(List(ByteString("<!-- Comments --><xml><header></header><body><ti"), ByteString("tle>hello</title></body></xml>")))
    val instructions = Seq[XMLInstruction](
      XMLUpdate(Seq("xml", "header", "foo"), Some("bar"), isUpsert = true),
      XMLDelete(Seq("xml", "body", "title")))

    whenReady(source.runWith(parseToByteString(instructions))) { r =>
      r.utf8String shouldBe "<?xml version=\"1.0\"?><!-- Comments --><xml><header><foo>bar</foo></header><body></body></xml>"
    }
  }
}
