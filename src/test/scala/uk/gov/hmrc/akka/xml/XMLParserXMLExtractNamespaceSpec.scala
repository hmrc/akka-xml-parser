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
import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}

class XMLParserXMLExtractNamespaceSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {

  val f = fixtures
  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  import f._

  behavior of "CompleteChunkStage#parser"


  it should "Parse and extract several non-default namespaces" in {

    val testXMLX =
      <ns5:GovTalkMessage
      xmlns:ns0="http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/13-14/2"
      xmlns:ns2="http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/15-16/1"
      xmlns:ns5="http://www.govtalk.gov.uk/CM/envelope"
      xmlns:ns1="http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/14-15/1"
      xmlns:ns3="http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/16-17/1"
      xmlns:ns4="http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/17-18/1"
      xmlns="">
        <ns5:EnvelopeVersion>2.0</ns5:EnvelopeVersion>
        <ns5:Header></ns5:Header>
        <ns5:GovTalkDetails></ns5:GovTalkDetails>
      </ns5:GovTalkMessage>

    val source = Source(List(ByteString(testXMLX.toString())))


    val paths = Seq[XMLInstruction](
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns:ns2" -> "http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/15-16/1")),
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns:BLABLA" -> "http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/13-14/2")),
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns" -> "http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/17-18/1")),
      XMLExtract(Seq("GovTalkMessage"), Map("xmlns" -> "http://www.govtalk.gov.uk/CM/envelope"))
    )

    val expected = Set(
      XMLElement(List("GovTalkMessage"), Map("xmlns:ns2" -> "http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/15-16/1"), Some("")),
      XMLElement(List("GovTalkMessage"), Map("xmlns:ns0" -> "http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/13-14/2"), Some("")),
      XMLElement(List("GovTalkMessage"), Map("xmlns:ns4" -> "http://www.govtalk.gov.uk/taxation/PAYE/RTI/EmployerPaymentSummary/17-18/1"), Some("")),
      XMLElement(List("GovTalkMessage"), Map("xmlns:ns5" -> "http://www.govtalk.gov.uk/CM/envelope"), Some("")),
      XMLElement(List(), Map(CompleteChunkStage.STREAM_SIZE -> "681"), Some(CompleteChunkStage.STREAM_SIZE))
    )

          whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe expected
    }

    whenReady(source.runWith(parseToByteString(paths))) { r =>
      whenReady(source.toMat(collectByteString)(Keep.right).run()) { t =>
        r shouldBe t
      }
    }
  }
}
