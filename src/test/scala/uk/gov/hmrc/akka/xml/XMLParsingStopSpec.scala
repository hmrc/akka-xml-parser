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

import akka.stream.scaladsl.{Keep, Source}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}

class XMLParsingStopSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {

  val f = fixtures
  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  import f._

  it should "Stop parsing when the passed in xPath is encountered" in {

    val source = Source(ParserTestHelpers.getBrokenMessage(ParserTestHelpers.sa100.toString, 100))

    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Qualifier")),
      XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Function")),
      XMLExtract(Seq("GovTalkMessage", "Body", "IRenvelope", "MTR", "SA100", "YourPersonalDetails", "NationalInsuranceNumber")), //This is in the body, will not be parsed
      XMLStopParsing(Seq("GovTalkMessage", "Body"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("HMRC-SA-SA100")),
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Function"), Map(), Some("submit")),
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Qualifier"), Map(), Some("request"))
    )

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r.filterNot(a => a.value == Some(FastParsingStage.STREAM_SIZE)) shouldBe expected
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }
  }

  it should "Notify if the payload exceeded the maximum allowed size" in {
    val source = Source(ParserTestHelpers.getBrokenMessage(ParserTestHelpers.sa100.toString, 100))

    val paths = Seq[XMLInstruction](XMLExtract(Seq("GovTalkMessage", "Header", "MessageDetails", "Class")))
    val expected = Set(
      XMLElement(List("GovTalkMessage", "Header", "MessageDetails", "Class"), Map(), Some("HMRC-SA-SA100")),
      XMLElement(List(), Map(), Some("Stream max size"))
    )

    whenReady(source.runWith(parseToXMLElements(paths, Some(200)))) { r =>
      r.filterNot(a => a.value == Some(FastParsingStage.STREAM_SIZE)) shouldBe expected
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }
  }


}
