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

import akka.util.ByteString
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.testkit.scaladsl.TestSource

class XmlEncodingYankerStageSpec extends FlatSpec with BeforeAndAfter {

  def createStream() = {
    val as = ActorSystem("XmlEncodingYankerStage")
    val am = ActorMaterializer()(as)
    val source = TestSource.probe[ByteString](as)
    val sink = TestSink.probe[ByteString](as)
    val chunk = XmlEncodingYankerStage.parser()

    //val (pub,sub) = source.via(chunk).alsoTo(Sink.foreach(a => println(a.utf8String))).toMat(sink)(Keep.both).run()(am)
    source.via(chunk).toMat(sink)(Keep.both).run()(am)
  }

  it should "Work on chopped up xml prlogs" in {
    val (pub,sub) = createStream()
    sub.request(10)
    pub.sendNext(ByteString("<?xml versi"))
    sub.expectNoMsg()
    pub.sendNext(ByteString("""on="1.0" enco"""))
    sub.expectNoMsg()
    pub.sendNext(ByteString("""ding="ISO-88"""))
    sub.expectNoMsg()
    pub.sendNext(ByteString("""59-1"?><GovTalkMe"""))
    sub.expectNext(ByteString("""<?xml version="1.0" encoding="UTF-8"?><GovTalkMe"""))
    //sub.request(1)
    pub.sendNext(ByteString("""ssage xsi:schemaLocation="http://www.govtalk.gov.uk/CM/envelope"""))
    sub.expectNext(ByteString("""ssage xsi:schemaLocation="http://www.govtalk.gov.uk/CM/envelope"""))
    pub.sendComplete()
  }

  it should "Work on whole xml prologs" in {
    val (pub,sub) = createStream()
    sub.request(10)
    pub.sendNext(ByteString("""<?xml version="1.0" encoding="ISO-8859-1"?><GovTalkMessage xsi:schemaLocation="http://www.govtalk."""))
    sub.expectNext(ByteString("""<?xml version="1.0" encoding="UTF-8"?><GovTalkMessage xsi:schemaLocation="http://www.govtalk."""))
  }

  it should "Let through xml without prolog1" in {
    val (pub,sub) = createStream()
    sub.request(10)
    pub.sendNext(ByteString("<xml><bo"))
    pub.sendNext(ByteString("dy><foo>"))
    pub.sendNext(ByteString("foo</foo><bar>test</bar></body></xml>"))
    sub.expectNext(ByteString("""<xml><body><foo>foo</foo><bar>test</bar></body></xml>"""))
    pub.sendComplete()
    sub.expectComplete()
  }

  it should "Let through xml without prolog" in {
    val (pub,sub) = createStream()
    sub.request(10)
    pub.sendNext(ByteString("""<ns5:GovTalkMessage xmlns=""><ns5:EnvelopeVersion>2.0</ns5:EnvelopeVersion>"""))
    sub.expectNext(ByteString("""<ns5:GovTalkMessage xmlns=""><ns5:EnvelopeVersion>2.0</ns5:EnvelopeVersion>"""))
  }

  it should "Work on short strings" in {
    val (pub,sub) = createStream()
    sub.request(10)
    pub.sendNext(ByteString("""<?xml version="1.0" encoding="ISO-88"""))
    sub.expectNoMsg()
    pub.sendComplete()
    sub.expectNext(ByteString("""<?xml version="1.0" encoding="ISO-88"""))
    sub.expectComplete()
  }


}
