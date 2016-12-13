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

//    "<xml><header><foo>foo123</foo><too>foo123</too></header></xml>"
//    ;"<xml><header><foo>foo123</foo><too>foo123</too></header></xml>"
//    "<xml><header><foo>bar"
//    ;"<xml><header><foo>foo123</foo><too>foo123</too></header></xml>"
//    "<xml><header><foo>bar</foo><too>foo123</too></header></xml>"
//    ;"<xml><header><foo>foo123</foo><too>foo123</too></header></xml>"
//    "<xml><header><foo>bar</foo><too>bar"
//    ;"<xml><header><foo>foo123</foo><too>foo123</too></header></xml>"
//    "<xml><header><foo>bar</foo><too>bar</too></header></xml>"

    val paths = Set[XMLInstruction](XMLUpdate(Seq("xml", "header", "foo"), Some("bar")))

    val expected = "<xml><header><foo>bar</foo></header></xml>"

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      println(r.utf8String)
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
      println("mmmmmmmmmmmmmm---" + r.utf8String)

      r.utf8String shouldBe "<xml><header><foo>bar</foo></header></xml>"
    }
  }

  it should "insert an element with attributes where it does not exist" in {
    val source = Source.single(ByteString("<xml></xml>"))

    val paths = Set[XMLInstruction](XMLUpdate(XPath("xml/bar"), Some("foo"), Map("attribute" -> "value"), isUpsert = true))

    val expected = "<xml><bar attribute=\"value\">foo</bar></xml>"

    whenReady(source.runWith(parseToByteString(paths))) { r =>

      println(r.utf8String)

      r.utf8String shouldBe expected
    }

  }

}
