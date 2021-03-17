/*
 * Copyright 2021 HM Revenue & Customs
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
  * Created by christine on 24/03/17.
  */
class XMLParserTransformSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {

  val f = fixtures

  import f._

  behavior of "TransformStage#parser"


  it should "transform successfully the specified data against a supplied function" in {
    val source = Source.single(ByteString("<xml><body><foo>test</foo></body></xml>"))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body></xml>"
    }
  }

  it should "transform successfully the specified data against a supplied function - if empty tags are present" in {
    val source = Source.single(ByteString("<xml><body><baz/><foo>test</foo></body></xml>"))
    val transformFunction: String => String = (string: String) => {
      if (string == "<xml><body><baz/><foo>test</foo></body>") {
        "<xml><body><foo>newValue</foo></body>"
      }
      else ""
    }
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body></xml>"
    }
  }

  it should "transform successfully the specified data against a supplied function - if empty tag is chunked" in {
    val source = Source(List(ByteString("<xml><body><b"), ByteString("az/><foo>test</foo></body></xml>")))
    val transformFunction: String => String = (string: String) => {
      if (string == "<xml><body><baz/><foo>test</foo></body>") {
        "<xml><body><foo>newValue</foo></body>"
      }
      else ""
    }
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body></xml>"
    }
  }

  it should "transform successfully the specified data when the provided data is chunked" in {

    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>test</fo"), ByteString("o></body></xml>")))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body></xml>"
    }

  }

  it should "transform successfully the specified data when the provided data is chunked after the transform has happened" in {

    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>test</fo"), ByteString("o></body><baz>ba"),
      ByteString("z</baz></xml>")))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body><baz>baz</baz></xml>"
    }

  }

  it should "validate over multiple chunks where end tag is also spit in chunks" in {
    val source = Source(List(ByteString("<xml><body>"), ByteString("<fo123o>test</fo"), ByteString("123"),
      ByteString("o><bar>test</bar></bo"), ByteString("dy><test>test</test></xml>")))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body><test>test</test></xml>"
    }
  }

  it should "validate over multiple chunks where end tag is spit into multiple chunks" in {
    val source = Source(List(ByteString("<xml><body>"), ByteString("<foo>test</fo"),
      ByteString("o><bar>test</bar></bo"), ByteString("d"), ByteString("y><test>test</test></xml>")))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body><test>test</test></xml>"
    }
  }

  it should "return an empty string if the closing tag opening tag is not found" in {
    val source = Source(List(ByteString("<xml><body>"), ByteString("<foo>test</fo"),
      ByteString("o><bar>test</bar></bo"), ByteString("d"), ByteString("y><test>test</test></xml>")))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "noTag"), transformFunction))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe ""
    }
  }

  it should "transform successfully and remove namespace prefixes when present" in {
    val source = Source.single(ByteString("<xml xmlns:t=\"http://www.govtalk\">" +
      "<t:body><t:foo>test</t:foo></t:body><t:test Attribute=\"Test\"></t:test></xml>"))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction),
      XMLRemoveNamespacePrefix(Seq("xml", "test"), removeForStartTag = true, removeForEndTag = true))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body><test Attribute=\"Test\"></test></xml>"
    }
  }

  it should "validate over multiple chunks where end tag is spit into multiple chunks and namespace is present" in {
    val source = Source(List(ByteString("<xml xmlns:t=\"http://www.govtalk\"><t:body>"),
      ByteString("<t:foo>test</t:fo"),
      ByteString("o><t:bar>test</t:bar></t:bo"), ByteString("d"), ByteString("y><tq>r</tq><t:test><t:test123>test123</t:test123></t:test></xml>")))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction),
      XMLRemoveNamespacePrefix(Seq("xml", "test"), true, true))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body><tq>r</tq><test><t:test123>test123</t:test123></test></xml>"
    }
  }


  it should "validate over multiple chunks where end tag is spit into multiple chunks and namespace is present end element only" in {
    val source = Source(List(ByteString("<xml xmlns:t=\"http://www.govtalk\"><t:body>"),
      ByteString("<t:foo>test</t:fo"),
      ByteString("o><t:bar>test</t:bar></t:bo"), ByteString("d"), ByteString("y><tq>r</tq><t:test><t:test123>test123</t:test123></t:test></xml>")))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction),
      XMLRemoveNamespacePrefix(Seq("xml", "test"), false, true))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body><tq>r</tq><t:test><t:test123>test123</t:test123></test></xml>"
    }
  }


  it should "validate over multiple chunks where end tag is spit into multiple chunks and namespace is present start element only" in {
    val source = Source(List(ByteString("<xml xmlns:t=\"http://www.govtalk\"><t:body>"),
      ByteString("<t:foo>test</t:fo"),
      ByteString("o><t:bar>test</t:bar></t:bo"), ByteString("d"), ByteString("y><tq>r</tq><t:test><t:test123>test123</t:test123></t:test></xml>")))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction),
      XMLRemoveNamespacePrefix(Seq("xml", "test"), true, false))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body><tq>r</tq><test><t:test123>test123</t:test123></t:test></xml>"
    }
  }


  it should "validate over multiple chunks where end tag is spit into multiple chunks and namespace is present start element only 0" in {
    val source = Source(List(ByteString("<xml xmlns:t=\"http://www.govtalk\"><t:body>"),
      ByteString("<t:foo>test</t:fo"),
      ByteString("o><t:bar>test</t:bar></t:bo"), ByteString("d"), ByteString("y><tq>r</tq><t:test><t:")
      , ByteString("test123>test123</t:"), ByteString("test123></t:test></xml>")))
    val transformFunction: String => String = (string: String) => "<xml><body><foo>newValue</foo></body>"
    val paths = Set[XMLInstruction](XMLTransform(Seq("xml"), Seq("xml", "body"), transformFunction),
      XMLRemoveNamespacePrefix(Seq("xml", "test"), true, false))

    whenReady(source.runWith(parseToByteStringViaTransform(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>newValue</foo></body><tq>r</tq><test><t:test123>test123</t:test123></t:test></xml>"
    }
  }
}
