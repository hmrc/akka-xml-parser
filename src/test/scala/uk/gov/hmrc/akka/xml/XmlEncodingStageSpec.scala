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
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.testkit.scaladsl.TestSource
import org.scalatest.concurrent.ScalaFutures

class XmlEncodingStageSpec extends FlatSpec with BeforeAndAfter with Matchers with ScalaFutures with XMLParserFixtures {

  val fix = fixtures

  import fix._


  it should "Work on chopped up xml prlogs" in {
    val (pub, sub) = createStream("UTF-8")
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
    val (pub, sub) = createStream("UTF-8")
    sub.request(10)
    pub.sendNext(ByteString("""<?xml version="1.0" encoding="ISO-8859-1"?><GovTalkMessage xsi:schemaLocation="http://www.govtalk."""))
    sub.expectNext(ByteString("""<?xml version="1.0" encoding="UTF-8"?><GovTalkMessage xsi:schemaLocation="http://www.govtalk."""))
  }

  it should "Let through xml without prolog with UTF-8 encoding" in {
    val (pub, sub) = createStream("UTF-8")
    sub.request(10)
    pub.sendNext(ByteString("<xml><bo"))
    pub.sendNext(ByteString("dy><foo>"))
    pub.sendNext(ByteString("foo</foo><bar>test</bar></body></xml>"))
    sub.expectNext(ByteString("""<xml><body><foo>foo</foo><bar>test</bar></body></xml>"""))
    pub.sendComplete()
    sub.expectComplete()
  }

  it should "Let through xml without prolog with Latin-1 encoding" in {
    val (pub, sub) = createStream("ISO-8859-1")
    sub.request(10)
    pub.sendNext(ByteString("""<ns5:GovTalkMessage xmlns=""><ns5:EnvelopeVersion>2.0</ns5:EnvelopeVersion>"""))
    sub.expectNext(ByteString("""<ns5:GovTalkMessage xmlns=""><ns5:EnvelopeVersion>2.0</ns5:EnvelopeVersion>"""))
  }

  it should "Work on short strings" in {
    val (pub, sub) = createStream("ISO-8859-1")
    sub.request(10)
    pub.sendNext(ByteString("""<?xml version="1.0" encoding="ISO-88"""))
    sub.expectNoMsg()
    pub.sendComplete()
    sub.expectNext(ByteString("""<?xml version="1.0" encoding="ISO-88"""))
    sub.expectComplete()
  }


  it should "Convert Latin-1 encoding to UTF-8" in {
    val message1 = """<?xml version="1.0" encoding="ISO-8859-1"?><GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope"><Body>££££££££££</Body>"""
    val (pub, sub) = createStream("UTF-8")
    sub.request(10)
    pub.sendNext(ByteString(message1.getBytes("ISO-8859-1")))
    val expected = ByteString.fromString("""<?xml version="1.0" encoding="UTF-8"?><GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope"><Body>££££££££££</Body>""", "UTF-8")
    sub.expectNext(expected)
  }

  it should "Convert UTF-8 encoding to Latin-1" in {
    val message1 = """<?xml version="1.0" encoding="UTF-8"?><GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope"><Body>££££££££££</Body>"""
    val (pub, sub) = createStream("ISO-8859-1")
    sub.request(10)
    pub.sendNext(ByteString(message1.getBytes("UTF-8")))
    val expected = ByteString.fromString("""<?xml version="1.0" encoding="ISO-8859-1"?><GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope"><Body>££££££££££</Body>""", "ISO-8859-1")
    sub.expectNext(expected)
  }

  it should "Convert UTF-8 encoding to Latin-1 for messages that are broken up into peaces" in {
    val msg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Body>£££££</Body></GovTalkMessage>"
    val messages = getBrokenMessage("UTF-8",msg)
    val (pub, sub) = createStreamFold("ISO-8859-1")
    sub.request(messages.length)
    messages.foreach(pub.sendNext _)
    pub.sendComplete() //Make that fold happen
    sub.expectNext(ByteString.fromString("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><Body>£££££</Body></GovTalkMessage>", "ISO-8859-1"))
  }

  it should "Convert Latin-1 encoding to UTF-8 for messages that are broken up into peaces" in {
    val msg = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><Body>£££££</Body></GovTalkMessage>"
    val messages = getBrokenMessage("ISO-8859-1",msg)
    val (pub, sub) = createStreamFold("UTF-8")
    sub.request(messages.length)
    messages.foreach(pub.sendNext _)
    pub.sendComplete() //Make that fold happen
    sub.expectNext(ByteString.fromString("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Body>£££££</Body></GovTalkMessage>", "UTF-8"))
  }


  it should "Convert default (UTF-8) encoding to Latin-1 in case no encoding was defined in the prolog" in {
    val msg = "<?xml version=\"1.0\"?><Body>£££££</Body></GovTalkMessage>"
    val messages = getBrokenMessage("UTF-8",msg)
    val (pub, sub) = createStreamFold("ISO-8859-1")
    sub.request(messages.length)
    messages.foreach(pub.sendNext _)
    pub.sendComplete() //Make that fold happen
    sub.expectNext(ByteString.fromString("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><Body>£££££</Body></GovTalkMessage>", "ISO-8859-1"))
  }

  it should "In the absence of prolog, we suppose UTF-8 for short messages" in {
    val msg = "<Body>£££££</Body></GovTalkMessage>"
    val messages = getBrokenMessage("UTF-8",msg)
    val (pub, sub) = createStreamFold("UTF-8")
    sub.request(messages.length)
    messages.foreach(pub.sendNext _)
    pub.sendComplete() //Make that fold happen
    sub.expectNext(ByteString.fromString("<Body>£££££</Body></GovTalkMessage>", "UTF-8"))

  }


  it should "In the absence of prolog, we suppose UTF-8 for longer messages" in {
    val msg = "<GovTalkMessage><Body>£££££</Body><aaaa>££££££<aaaa><aaaa>££££££<aaaa><aaaa>££££££<aaaa><aaaa>££££££<aaaa></GovTalkMessage>"
    val messages = getBrokenMessage("UTF-8",msg)
    val (pub, sub) = createStreamFold("UTF-8")
    sub.request(messages.length)
    messages.foreach(pub.sendNext _)
    pub.sendComplete() //Make that fold happen
    sub.expectNext(ByteString.fromString("<GovTalkMessage><Body>£££££</Body><aaaa>££££££<aaaa><aaaa>££££££<aaaa><aaaa>££££££<aaaa><aaaa>££££££<aaaa></GovTalkMessage>", "UTF-8"))

  }


  "Without the encoding changer stage the system" should "reject ISO-8859-1 encoding in the xml prolog regardless of actual encoding" in {
    val message1 = """<?xml version="1.0" encoding="ISO-8859-1"?><GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope"><Body>££££££££££</Body></GovTalkMessage>"""
    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"))
    )

    val source = Source.single(ByteString.fromString(message1, "UTF-8"))
    whenReady(source.runWith(parseToXMLElements(paths))) { r: Set[XMLElement] =>
      def seq2s(in: XMLElement): String = in.xPath.toString

      val orderedList = r.toList.sortWith((a, b) => seq2s(a) > seq2s(b))
      orderedList(0).attributes("Malformed") should startWith("Unsupported encoding 'ISO-8859-1': only UTF-8 and US-ASCII support by async")
      orderedList(1).attributes(CompleteChunkStage.STREAM_SIZE) shouldBe ("155")
    }
  }

  "Without the encoding changer stage the system " should "reject ISO-8859-1 encoded special characters" in {
    val message1 =
      """<?xml version="1.0" encoding="UTF-8"?><GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope"><Body>Here: úúú</Body></GovTalkMessage>"""
    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "Body"))
    )

    val source = Source.single(ByteString.fromString(message1, "ISO-8859-1"))
    whenReady(source.runWith(parseToXMLElements(paths))) { r: Set[XMLElement] =>
      def seq2s(in: XMLElement): String = in.xPath.toString

      val orderedList = r.toList.sortWith((a, b) => seq2s(a) > seq2s(b))
      orderedList(0).value.get should startWith(FastParsingStage.MALFORMED_STATUS)
    }
  }


  def createStream(encoding: String) = {
    val as = ActorSystem("XmlEncodingYankerStage")
    val am = ActorMaterializer()(as)
    val source = TestSource.probe[ByteString](as)
    val sink = TestSink.probe[ByteString](as)
    val chunk = XmlEncodingStage.parser(encoding)

    //source.via(chunk).alsoTo(Sink.foreach(a => println(a.utf8String))).toMat(sink)(Keep.both).run()(am)  //use this for testing
    source.via(chunk).toMat(sink)(Keep.both).run()(am)
  }

  //Create a stream which collects all the elements together (fold) before releasing them to the TestSink for checking
  def createStreamFold(encoding: String) = {
    val as = ActorSystem("XmlEncodingYankerStage")
    val am = ActorMaterializer()(as)
    val source = TestSource.probe[ByteString](as)
    val sink = TestSink.probe[ByteString](as)
    val chunk = XmlEncodingStage.parser(encoding)

    //source.via(chunk).fold(ByteString.empty)((sum, i) => sum ++ i).alsoTo(Sink.foreach(a => println(a.utf8String))).toMat(sink)(Keep.both).run()(am) //use this for testing
    source.via(chunk).fold(ByteString.empty)((sum, i) => sum ++ i).toMat(sink)(Keep.both).run()(am)
  }

  /**
    * Break up the xml to random peaces, so we could simulate a streaming data
    *
    * @param originalEncoding
    * @return
    */
  def getBrokenMessage(originalEncoding: String, message2Break:String): List[ByteString] = {
    val rnd = scala.util.Random
    var sum = 0
    val lengths = new scala.collection.mutable.ArrayBuffer[Int]()
    while (sum < message2Break.length) { //Create a list of lengts we are gonna cut our string at
      val next = rnd.nextInt(30) + 1
      sum += next
      lengths += sum
    }
    lengths(lengths.length - 1) = message2Break.size
    var startAt = 0
    lengths.map { endAt =>
      val part = message2Break.substring(startAt, endAt)
      startAt = endAt
      ByteString(part.getBytes(originalEncoding))
    }.toList
  }


}
