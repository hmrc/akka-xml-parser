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

///*
// * Copyright 2016 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.akka.xml
//
//import akka.stream.scaladsl.Source
//import akka.util.ByteString
//import org.scalatest.{FlatSpec, Matchers}
//import org.scalatest.concurrent.{Eventually, ScalaFutures}
//import org.scalatest.mock.MockitoSugar
//import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
//
///**
//  * Created by abhishek on 15/12/16.
//  */
//class XMLParserXmlDeleteSpec extends FlatSpec
//  with Matchers
//  with ScalaFutures
//  with MockitoSugar
//  with Eventually
//  with XMLParserFixtures {
//
//  val f = fixtures
//
//  import f._
//
//  behavior of "AkkaXMLParser#parser"
//
//  it should "delete an element when instructed to do so" in {
//    val source = Source.single(ByteString("<xml><body><foo>foo</foo></body></xml>"))
//    val paths = Set[XMLInstruction](XMLDelete(Seq("xml", "body", "foo")))
//    whenReady(source.runWith(parseToByteString(paths))) { r =>
//      r.utf8String "<xml><body></body></xml>"
//    }
//  }
//
//}
