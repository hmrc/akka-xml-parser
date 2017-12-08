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
import org.scalatest.time.{Millis, Seconds, Span}

/**
  * Created by abhishek on 15/12/16.
  */
class XMLParserXmlValidateSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {

  val f = fixtures
  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  import f._

  behavior of "ParsingStage#parser"

  def findAttribute(set:Set[XMLElement], attribute:String):XMLElement = {
    set.find(a => a.attributes.get(attribute).isDefined).getOrElse(XMLElement(Seq.empty[String]))
  }

  it should "validate successfully the specified data against a supplied function" in {
    val source = Source.single(ByteString("ï»¿<xml><body><foo>test</foo><bar>test</bar><test></test></body></xml>"))
    val validatingFunction: String => Option[ParserValidationError] = (string: String) =>
      if (string == "<body><foo>test</foo><bar>test</bar>") None else Some(new ParserValidationError {})
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body", "test"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "67")
        , Some(CompleteChunkStage.STREAM_SIZE)))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar><test></test></body></xml>"
    }
  }

  it should "fail validation when the specified data does not pass the supplied validation function" in {
    val source = Source.single(ByteString("<xml><body><foo>fail</foo><bar>fail</bar></body><test>foo</test></xml>"))
    val error = new ParserValidationError {}
    val validatingFunction: String => Option[ParserValidationError] = (string: String) => if (string ==
      "<body><foo>test</foo><bar>test</bar></body>") None
    else Some(error)
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "test"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(100)))) { r =>
      findAttribute(r,ParsingStage.VALIDATION_INSTRUCTION_FAILURE).attributes(ParsingStage.VALIDATION_INSTRUCTION_FAILURE) contains ("uk.gov.hmrc.akka.xml.XMLParserXmlValidateSpec")
    }
  }

  it should "validate over multiple chunks" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body><test>foo</test></xml>")))
    val validatingFunction: String => Option[ParserValidationError] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(new ParserValidationError {})
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "test"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(100)))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "70"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body><test>foo</test></xml>"
    }
  }

  it should "validate over multiple chunks where start tag is also spit in chunks - message size check" in {
    val source = Source(List(ByteString("<xml><body>"), ByteString("<fo"), ByteString("123"),
      ByteString("o>test</fo123o><bar>test</bar></bo"), ByteString("dy></xml>")))
    val validatingFunction: String => Option[ParserValidationError] = (string: String) =>
      if (string == "<body><fo123o>test</fo123o><bar>test</bar></body></xml>") None else Some(new ParserValidationError {})
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "test"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(100)))) { r =>
      findAttribute(r,ParsingStage.VALIDATION_INSTRUCTION_FAILURE).attributes(ParsingStage.VALIDATION_INSTRUCTION_FAILURE) contains ("uk.gov.hmrc.akka.xml.XMLParserXmlValidateSpec")

    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><fo123o>test</fo123o><bar>test</bar></body></xml>"
    }
  }


  it should "validate over multiple chunks where start tag is also spit in chunks" in {
    val source = Source(List(ByteString("<xml><body>"), ByteString("<fo123o>test</fo"), ByteString("123"),
      ByteString("o><bar>test</bar></body><tes"), ByteString("t>test</test></xml>")))
    val validatingFunction: String => Option[ParserValidationError] = (string: String) =>
      if (string == "<body><fo123o>test</fo123o><bar>test</bar></body>") None else Some(new ParserValidationError {})
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "test"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(100)))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "77"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><fo123o>test</fo123o><bar>test</bar></body><test>test</test></xml>"
    }
  }

  it should "fail validation over multiple chunks" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>foo</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val error = new ParserValidationError {}
    val validatingFunction: String => Option[ParserValidationError] = (string: String) =>
      if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(error)
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      findAttribute(r,ParsingStage.VALIDATION_INSTRUCTION_FAILURE).attributes(ParsingStage.VALIDATION_INSTRUCTION_FAILURE) contains ("uk.gov.hmrc.akka.xml.XMLParserXmlValidateSpec")
    }
  }

  it should "fail validation if no validation tags is found within max allowed validation size" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>"), ByteString("foo</foo><bar>test</bar></body></xml>")))
    val error = new ParserValidationError {}
    val validatingFunction: String => Option[ParserValidationError] = (string: String) => if (string == "<body><foo>test</foo></body>") None else Some(error)
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "bar"), Seq("xml", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(5)))) { r =>
      findAttribute(r,ParsingStage.NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE) shouldBe XMLElement(List(), Map(ParsingStage.NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE -> ""), Some(ParsingStage.NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE))
      findAttribute(r,CompleteChunkStage.STREAM_SIZE) shouldBe XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "8"), Some(CompleteChunkStage.STREAM_SIZE))
    }
  }

  it should "validate with self closing tags" in {
    val source = Source.single(ByteString("<xml><foo/><bar>bar</bar><test/></xml>"))
    val validatingFunction: String => Option[ParserValidationError] = (string: String) => if (string == "<xml><foo/><bar>bar</bar>") None else Some(new ParserValidationError {})
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml"), Seq("test"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(100)))) { r =>
      findAttribute(r,ParsingStage.VALIDATION_INSTRUCTION_FAILURE).attributes(ParsingStage.VALIDATION_INSTRUCTION_FAILURE) contains ("uk.gov.hmrc.akka.xml.XMLParserXmlValidateSpec")
    }
  }

  it should "fail validation if the start tag is not found" in {
    val source = Source.single(ByteString("<xml><body><bar>bar</bar><test/></body></xml>"))
    val validatingFunction = (string: String) => None
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "body", "foo"), Seq("xml", "body", "test"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(100)))) { r =>
      findAttribute(r,ParsingStage.PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE) shouldBe XMLElement(List(), Map(ParsingStage.PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE -> ""), Some(ParsingStage.PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE))
      findAttribute(r,CompleteChunkStage.STREAM_SIZE) shouldBe XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "45"), Some(CompleteChunkStage.STREAM_SIZE))
    }
  }

  it should "pass validation if the start tag is not found" in {
    val source = Source.single(ByteString("<xml><body><foo>foo</foo></body></xml>"))
    val validatingFunction = (string: String) => None
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "body", "foo"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(100)))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "38"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>foo</foo></body></xml>"
    }
  }

  it should "validate over multiple chunks - size within limits" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>test</foo><bar>t"), ByteString("est</bar></body><fo"),
      ByteString("oter>footer</fo"), ByteString("oter></xml>")))
    val validatingFunction: String => Option[ParserValidationError] = (string: String) => if (string == "<body><foo>test</foo>")
      None
    else Some(new ParserValidationError {})
    val paths = Seq[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(50)))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "77"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body><footer>footer</footer></xml>"
    }
  }

  it should "validate over multiple chunks - making sure that the END_DOCUMENT event is not calling the validating function multiple times" in {
    val source = Source.single(
      ByteString("<xml><root><foo>test</foo><body><taz>bad</taz></body></root></xml>"))
    val validatingFunction: String => Option[ParserValidationError] = (string: String) => if (string == "<root><foo>test</foo>")
      None
    else Some(new ParserValidationError {})
    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("xml", "root", "foo")),
      XMLExtract(Seq("xml", "root", "taz")),
      XMLValidate(Seq("xml", "root"), Seq("xml", "root", "body"), validatingFunction)
    )

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(100)))) { r =>
      r shouldBe Set(
        XMLElement(List("xml", "root", "foo"), Map(),Some("test")),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "66"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><root><foo>test</foo><body><taz>bad</taz></body></root></xml>"
    }
  }

}
