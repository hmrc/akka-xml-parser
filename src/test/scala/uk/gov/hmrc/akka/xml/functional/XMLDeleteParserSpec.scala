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

package uk.gov.hmrc.akka.xml.functional

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import uk.gov.hmrc.akka.xml.{XMLDelete, XMLInstruction, XPath}

import scala.concurrent.Future

/**
  * Created by william on 14/03/17.
  */
class XMLDeleteParserSpec extends WordSpec
  with Matchers with ScalaFutures with BeforeAndAfterAll {

  private implicit val sys = ActorSystem("xml-parser")
  private implicit val mat = ActorMaterializer()

  private val byteStringSink: Sink[ParserData, Future[ByteString]] = Flow[ParserData]
    .map(_.data)
    .toMat(Sink.fold(ByteString.empty)(_ ++ _))(Keep.right)

  override def afterAll(): Unit = {
    mat.shutdown()
    sys.terminate()
  }

  "parse" should {

    "return a flow which, when ran into a sink, will produce a ByteString" in {
      val xmlSrc = Source(
        List(
          ByteString("<hello"), ByteString("world>"),
          ByteString("foo"), ByteString("bar"),
          ByteString("</helloworld>")
        )
      )

      val parser = new XMLDeleteParser()

      val res: Future[ByteString] = xmlSrc.via(parser.parse(Set.empty)).runWith(byteStringSink)

      whenReady(res) {
        _.utf8String shouldBe "<helloworld>foobar</helloworld>"
      }
    }

    "return a flow which, when ran into a sink, will delete the specified elements" in {
      val xmlSrc = Source(
        List(
          ByteString("""<root xmlns="http://www.govtalk.gov.uk/CM/envelope">"""),
/*          ByteString("<hello"), */ ByteString("""<helloworld foo="bar">"""),
          ByteString("foo"), ByteString("bar"),
          ByteString("</helloworld>"),
          ByteString("<hello"), ByteString("""world foo="bar">"""),
          ByteString("foo"), ByteString("bar"),
          ByteString("</helloworld>"),
          ByteString("</root>")
        )
      )

      val instructions = Set[XMLInstruction](
        XMLDelete(XPath("root/helloworld"))
      )

      val parser = new XMLDeleteParser()

      val res: Future[ByteString] = xmlSrc.via(parser.parse(instructions)).runWith(byteStringSink)

      whenReady(res) {
        _.utf8String shouldBe """<root xmlns="http://www.govtalk.gov.uk/CM/envelope"><helloworld foo="bar">foobar</helloworld></root>"""
      }
    }

  }

}
