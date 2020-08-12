/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.testkit.scaladsl.TestSource

class CompleteChunkSpec extends FlatSpec {

  def createStream() = {
    val as = ActorSystem("CompleteChunkSpec")
    val am = ActorMaterializer()(as)
    val source = TestSource.probe[ByteString](as)
    val sink = TestSink.probe[ParsingData](as)
    val chunk = CompleteChunkStage.parser()

    //source.map(a => {println("<< " + a.decodeString("UTF-8"));a}).via(chunk).alsoTo(Sink.foreach(a => println(">> " + a))).toMat(sink)(Keep.both).run()(am)  //Use for debugging
    source.via(chunk).toMat(sink)(Keep.both).run()(am)
  }


  it should "only let whole xml tags through" in {
    //This is our entire test xml: <xml><header><id>Joska</id><aa>Pista</aa><bb>Miska</bb></header></xml>
    val (pub,sub) = createStream()
    sub.request(20)
    pub.sendNext(ByteString("<xml><hea"))
    sub.expectNext(ParsingData(ByteString("<xml>"), Set.empty, 5))
    pub.sendNext(ByteString("der><id>Jo"))
    sub.expectNext(ParsingData(ByteString("<header><id>Jo"), Set.empty, 19))
    pub.sendNext(ByteString("ska</i"))
    sub.expectNext(ParsingData(ByteString("ska"), Set.empty, 22))
    pub.sendNext(ByteString("d><aa>Pista</a"))
    sub.expectNext(ParsingData(ByteString("</id><aa>Pista"), Set(), 36))
    pub.sendNext(ByteString("a><bb>Mis"))
    sub.expectNext(ParsingData(ByteString("</aa><bb>Mis"), Set(), 48))
    pub.sendNext(ByteString("ka</bb></he"))
    sub.expectNext(ParsingData(ByteString("ka</bb>"), Set(), 55))
    pub.sendNext(ByteString("ader></xml>"))
    sub.expectNext(ParsingData(ByteString("</header></xml>"), Set(), 70))
    pub.sendComplete()
    sub.expectNext(ParsingData(ByteString.empty,  Set(XMLElement(List(),Map("Stream Size" -> "70"), Some("Stream Size"))), 70))
    sub.expectComplete()
  }




}
