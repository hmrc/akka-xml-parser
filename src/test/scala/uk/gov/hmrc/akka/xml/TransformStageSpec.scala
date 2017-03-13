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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures

/**
  * Created by william on 08/03/17.
  */
class TransformStageSpec extends WordSpec with Matchers with ScalaFutures {

  private implicit val sys = ActorSystem("transform")
  private implicit val mat = ActorMaterializer()

  "transform" should {

    "return a flow which outputs data equal to the input data" in {
      val source = Source(
        List(ByteString("<root><inside>hello"), ByteString("world</inside></root>"))
      )

      val transformer = TransformStage.transform(XMLTransform(Seq("root", "inside"), _ => "test"))

      val res = source.via(transformer).runFold(ByteString.empty)(_ ++ _)

      whenReady(res) {
        _.utf8String shouldBe "<root><inside>test</inside></root>"
      }
    }

//    "return a flow which outputs transformed data according to the instruction given" in {
//      println(">>>>>> Test 2")
//      val source = Source(
//        List(ByteString("<root>"), ByteString("</root>"))
//      )
//
//      val transformer = TransformStage.transform(
//        Set[XMLInstruction](XMLTransform(Seq("root"), _ => None))
//      )
//
//      val res = source.via(transformer).runFold(ByteString.empty)(_ ++ _)
//
//      whenReady(res) {
//        _.utf8String shouldBe "<root></root>"
//      }
//    }

  }

}
