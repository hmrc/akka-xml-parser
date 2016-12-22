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

  behavior of "AkkaXMLParser#parser"


  it should "validate successfully the specified data against a supplied function" in {
    val source = Source.single(ByteString("<xml><body><foo>test</foo><bar>test</bar></body></xml>"))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set.empty
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body></xml>"
    }

  }
  it should "fail validation when the specified data does not pass the supplied validation function" in {
    val source = Source.single(ByteString("<xml><body><foo>fail</foo><bar>fail</bar></body></xml>"))
    val error = new NoStackTrace {}
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(error)
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths)).failed) { r =>
      r shouldBe error
    }
  }

  it should "validate over multiple chunks" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set.empty
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body></xml>"
    }
  }

  it should "fail validation over multiple chunks" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>foo</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val error = new NoStackTrace {}
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(error)
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths)).failed) { r =>
      r shouldBe error
    }
  }

  it should "validate with self closing tags" in {
    val source = Source.single(ByteString("<xml><foo/><bar>bar</bar></xml>"))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<xml><foo/><bar>bar</bar></xml>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml"), Seq("xml"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set.empty
    }
  }

  it should "fail validation if the start tag is not found" in {
    val source = Source.single(ByteString("<xml><body><bar>bar</bar></body></xml>"))
    val validatingFunction = (string: String) => None
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body", "foo"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths)).failed) { r =>
      r shouldBe an[XMLValidationException]
    }
  }

  it should "fail validation if the end tag is not found" in {
    val source = Source.single(ByteString("<xml><body><foo>foo</foo></body></xml>"))
    val validatingFunction = (string: String) => None
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body", "foo"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths)).failed) { r =>
      r shouldBe an[XMLValidationException]
    }
  }

  it should "return a malformed status if the xml isn't properly closed off with an end tag" in {
    val source = Source.single(ByteString("<foo>bar"))
    val paths = Set[XMLInstruction](XMLExtract(XPath("foo")))
    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<foo>bar"
    }
  }

  it should "return a malformed status if the xml isn't properly closed off with an end tag (multiple chunks)" in {
    val source = Source(List(ByteString("<xml><foo>b"), ByteString("ar"), ByteString("</foo><hello>wor"), ByteString("ld</hello>")))

    val paths = Set[XMLInstruction](XMLExtract(XPath("xml/foo")))
    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List("xml", "foo"), Map.empty, Some("bar")),
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><foo>bar</foo><hello>world</hello>"
    }
  }

}
