/*
 * Copyright 2022 HM Revenue & Customs
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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.testkit.scaladsl.{TestSink, TestSource}
import org.apache.pekko.util.ByteString
import org.scalatest.flatspec.AnyFlatSpec

class ParsingStageSpec extends AnyFlatSpec {


  def createStream(instructions: Seq[XMLInstruction], validationMaxSize: Option[Int] = None) = {
    val as = ActorSystem("CompleteChunkSpec")
    val am = Materializer(as)
    val source = TestSource.probe[ParsingData](as)
    val sink = TestSink.probe[(ByteString, Set[XMLElement])](as)
    val chunk = ParsingStage.parser(instructions)

    //source.via(chunk).alsoTo(Sink.foreach(a => println(">> " + a._1.decodeString("UTF-8") + " | " + a._2))).toMat(sink)(Keep.both).run()(am)
    source.via(chunk).toMat(sink)(Keep.both).run()(am)
  }

  def getEmptyResult(in: String): (ByteString, Set[XMLElement]) = {
    (ByteString(in), Set.empty[XMLElement])
  }

  it should "Extract XMLInstruction from a xml broken into pieces (even xml tags are broken up)" in {
    val idHeader = XMLExtract(List("xml", "header", "id"))
    val aaHeader = XMLExtract(List("xml", "header", "aa"))

    //This is our entire test xml: <xml><header><id>Joska</id><aa>Pista</aa><bb>Miska</bb><cc/><dd/></header></xml>  //The test xml

    val (pub, sub) = createStream(Seq(idHeader, aaHeader))
    sub.request(10)
    pub.sendNext(ParsingData(ByteString("<xml><hea"), Set.empty, 5))
    sub.expectNext(getEmptyResult("<xml><hea"))
    pub.sendNext(ParsingData(ByteString("der><id>Jo"), Set.empty, 19))
    sub.expectNext(getEmptyResult("der><id>Jo"))
    pub.sendNext(ParsingData(ByteString("ska</i"), Set.empty, 26))
    sub.expectNext(getEmptyResult("ska</i"))
    pub.sendNext(ParsingData(ByteString("d><aa>Pista</a"), Set.empty, 32))
    sub.expectNext((ByteString("d><aa>Pista</a"), Set(XMLElement(List("xml", "header", "id"), Map(), Some("Joska")))))
    pub.sendNext(ParsingData(ByteString("a><bb>Mis"), Set.empty, 38))
    sub.expectNext((ByteString("a><bb>Mis"), Set(XMLElement(List("xml", "header", "aa"), Map(), Some("Pista")))))
    pub.sendNext(ParsingData(ByteString("ka</bb><cc/"), Set.empty, 50))
    sub.expectNext(getEmptyResult("ka</bb><cc/"))
    pub.sendNext(ParsingData(ByteString("><dd"), Set.empty, 57))
    sub.expectNext(getEmptyResult("><dd"))
    pub.sendNext(ParsingData(ByteString("/></header></xml>"), Set.empty, 57))
    sub.expectNext(getEmptyResult("/></header></xml>"))
    pub.sendComplete()
  }

}
