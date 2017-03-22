package uk.gov.hmrc.akka.xml

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar

/**
  * Created by abhishek on 22/03/17.
  */
class XMLInsertAfterSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {
  val f = fixtures

  import f._

  behavior of "XMLInsertAfter"

  it should "insert a given element after the provided xPath" in {
    val source = Source.single(ByteString("<xml><header><id>12345</id></header></xml>"))
    val instruction = Set[XMLInstruction](XMLInsertAfter(Seq("xml", "header", "id"), "<hello>world</hello>"))

    whenReady(source.runWith(parseToXMLElements(instruction))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "42"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(instruction))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id><hello>world</hello></header></xml>"
    }
  }

  it should "insert a given element after the provided xPath - with chunking at start tag" in {
    val source = Source(List(ByteString("<xml><header><i"),ByteString("d>12345</id></header></xml>")))
    val instruction = Set[XMLInstruction](XMLInsertAfter(Seq("xml", "header", "id"), "<hello>world</hello>"))

    whenReady(source.runWith(parseToXMLElements(instruction))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "42"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(instruction))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id><hello>world</hello></header></xml>"
    }
  }

  it should "insert a given element after the provided xPath - with chunking at end tag" in {
    val source = Source(List(ByteString("<xml><header><id>12345</i"),ByteString("d></header></xml>")))
    val instruction = Set[XMLInstruction](XMLInsertAfter(Seq("xml", "header", "id"), "<hello>world</hello>"))

    whenReady(source.runWith(parseToXMLElements(instruction))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "42"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(instruction))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id><hello>world</hello></header></xml>"
    }
  }

  it should "insert a given element after the provided xPath and delete if already present" in {
    val source = Source(List(ByteString("<xml><header><id>12345</i"),ByteString("d><hello>old world</hello></header></xml>")))
    val instruction = Set[XMLInstruction](
      XMLInsertAfter(Seq("xml", "header", "id"), "<hello>new world</hello>"),
      XMLDelete(Seq("xml", "header", "hello"))
    )

    whenReady(source.runWith(parseToXMLElements(instruction))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "66"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(instruction))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id><hello>new world</hello></header></xml>"
    }
  }

  it should "insert a given element after the provided xPath and donot delete anything if not present" in {
    val source = Source(List(ByteString("<xml><header><id>12345</i"),ByteString("d></header></xml>")))
    val instruction = Set[XMLInstruction](
      XMLInsertAfter(Seq("xml", "header", "id"), "<hello>new world</hello>"),
      XMLDelete(Seq("xml", "header", "hello"))
    )

    whenReady(source.runWith(parseToXMLElements(instruction))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "42"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }

    whenReady(source.runWith(parseToByteString(instruction))) { r =>
      r.utf8String shouldBe "<xml><header><id>12345</id><hello>new world</hello></header></xml>"
    }
  }
}