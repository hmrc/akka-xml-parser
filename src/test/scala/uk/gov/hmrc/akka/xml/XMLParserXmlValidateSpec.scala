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

/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http:www.apache.org/licenses/LICENSE-2.0
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
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}

import scala.util.control.NoStackTrace

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

  import f._

  behavior of "ParsingStage#parser"


  it should "validate successfully the specified data against a supplied function" in {
    val source = Source.single(ByteString("<xml><body><foo>test</foo><bar>test</bar></body></xml>"))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "54")
        , Some(CompleteChunkStage.STREAM_SIZE)))
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body></xml>"
    }
  }

  it should "fail validation when the specified data does not pass the supplied validation function" in {
    val source = Source.single(ByteString("<xml><body><foo>fail</foo><bar>fail</bar></body></xml>"))
    val error = new ParserValidationError {}
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string ==
      "<body><foo>test</foo><bar>test</bar></body>") None
    else Some(error)
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r.last.attributes(ParsingStage.VALIDATION_INSTRUCTION_FAILURE) contains ("uk.gov.hmrc.akka.xml.XMLParserXmlValidateSpec")
    }
  }

  it should "validate over multiple chunks" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "54"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body></xml>"
    }
  }

  //  it should "validate over multiple chunks where start tag is also spit in chunks - message size check" in {
  //    val source = Source(List(ByteString("<xml><body>"), ByteString("<fo"), ByteString("123"),
  //      ByteString("o>test</fo123o><bar>test</bar></bo"), ByteString("dy></xml>")))
  //    val validatingFunction: String => Option[Throwable] = (string: String) =>
  //      if (string == "<body><fo123o>test</fo123o><bar>test</bar></body>") None else Some(new NoStackTrace {})
  //    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))
  //
  //    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
  //      r shouldBe Set(
  //        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "60"), Some(CompleteChunkStage.STREAM_SIZE))
  //      )
  //    }
  //    whenReady(source.runWith(parseToByteString(paths))) { r =>
  //      r.utf8String shouldBe "<xml><body><fo123o>test</fo123o><bar>test</bar></body></xml>"
  //    }
  //  }

  //
  //  it should "validate over multiple chunks where end tag is also spit in chunks" in {
  //    val source = Source(List(ByteString("<xml><body>"), ByteString("<fo123o>test</fo"), ByteString("123"),
  //      ByteString("o><bar>test</bar></bo"), ByteString("dy></xml>")))
  //    val validatingFunction: String => Option[Throwable] = (string: String) =>
  //      if (string == "<body><fo123o>test</fo123o><bar>test</bar></body>") None else Some(new NoStackTrace {})
  //    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))
  //
  //    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
  //      r shouldBe Set(
  //        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "60"), Some(CompleteChunkStage.STREAM_SIZE))
  //      )
  //    }
  //    whenReady(source.runWith(parseToByteString(paths))) { r =>
  //      r.utf8String shouldBe "<xml><body><fo123o>test</fo123o><bar>test</bar></body></xml>"
  //    }
  //  }


  it should "fail validation over multiple chunks" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>foo</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val error = new ParserValidationError {}
    val validatingFunction: String => Option[Throwable] = (string: String) =>
      if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(error)
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      println(r)
      r.last.attributes(ParsingStage.VALIDATION_INSTRUCTION_FAILURE) contains ("uk.gov.hmrc.akka.xml.XMLParserXmlValidateSpec")
    }
  }

  it should "fail validation if no validation tags is found within max allowed validation size" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>"), ByteString("foo</foo><bar>test</bar></body></xml>")))
    val error = new ParserValidationError {}
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(error)
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "bar"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(5)))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(ParsingStage.NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE -> ""), Some(ParsingStage.NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE)),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "16"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
  }

  it should "validate with self closing tags" in {
    val source = Source.single(ByteString("<xml><foo/><bar>bar</bar></xml>"))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<xml><foo/><bar>bar</bar></xml>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml"), Seq("xml"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "31"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
  }

  it should "fail validation if the start tag is not found" in {
    val source = Source.single(ByteString("<xml><body><bar>bar</bar></body></xml>"))
    val validatingFunction = (string: String) => None
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body", "foo"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(ParsingStage.PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE -> ""), Some(ParsingStage.PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE)),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "38"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
  }

  it should "fail validation if the end tag is not found" in {
    val source = Source.single(ByteString("<xml><body><foo>foo</foo></body></xml>"))
    val validatingFunction = (string: String) => None
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body", "foo"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(ParsingStage.PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE -> ""), Some(ParsingStage.PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE)),
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "38"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
  }

  it should "return a malformed status if the xml isn't properly closed off with an end tag" in {
    val source = Source.single(ByteString("<foo>foo<bar>bar"))
    val paths = Set[XMLInstruction](XMLExtract(XPath("foo")))
    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "16"), Some(CompleteChunkStage.STREAM_SIZE)),
        XMLElement(Nil, Map(ParsingStage.MALFORMED_STATUS ->
          (ParsingStage.XML_START_END_TAGS_MISMATCH + "foo, bar")), Some(ParsingStage.MALFORMED_STATUS))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<foo>foo<bar>bar"
    }
  }

  it should "return a malformed status if the xml isn't properly closed off with an end tag (multiple chunks)" in {
    val source = Source(List(ByteString("<xml><foo>b"), ByteString("ar"), ByteString("</foo><hello>wor"), ByteString("ld</hello>")))

    val paths = Set[XMLInstruction](XMLExtract(XPath("xml/foo")))
    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "39"), Some(CompleteChunkStage.STREAM_SIZE)),
        XMLElement(List("xml", "foo"), Map.empty, Some("bar")),
        XMLElement(Nil, Map(ParsingStage.MALFORMED_STATUS ->
          (ParsingStage.XML_START_END_TAGS_MISMATCH + "xml")), Some(ParsingStage.MALFORMED_STATUS))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><foo>bar</foo><hello>world</hello>"
    }
  }
  it should "validate over multiple chunks - size within limits" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>test</foo><bar>t"), ByteString("est</bar></body><fo"),
      ByteString("oter>footer</fo"), ByteString("oter></xml>")))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>")
      None
    else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths, None, Some(50)))) { r =>
      r shouldBe Set(
        XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "77"), Some(CompleteChunkStage.STREAM_SIZE))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body><footer>footer</footer></xml>"
    }
  }

}
