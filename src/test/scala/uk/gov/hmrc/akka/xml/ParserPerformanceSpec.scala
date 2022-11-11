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

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.TestSource
import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.mockito.scalatest.MockitoSugar

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.time.{Span, Seconds}

/**
  * This case tests TE parsing performance for a larger file.
  */
class ParserPerformanceSpec extends FlatSpec with Matchers with ScalaFutures with MockitoSugar with Eventually with XMLParserFixtures {


  val XMLNS = "xmlns"
  val GOV_TALK_NAMESPACE = "http://www.govtalk.gov.uk/CM/envelope"
  val GOV_TALK_MESSAGE = "GovTalkMessage"
  val ENVELOPE_VERSION = "EnvelopeVersion"
  val HEADER = "Header"
  val MESSAGE_CLASS = "Class"
  val QUALIFIER = "Qualifier"
  val FUNCTION = "Function"
  val TRANSACTION_ID = "TransactionID"
  val CORRELATION_ID = "CorrelationID"
  val RESPONSE_ENDPOINT = "ResponseEndPoint"
  val POLL_INTERVAL = "PollInterval"
  val TRANSFORMATION = "Transformation"
  val AUDIT_ID = "AuditID"
  val GATEWAY_TEST = "GatewayTest"
  val GATEWAY_TIMESTAMP = "GatewayTimestamp"
  val SENDER_ID = "SenderID"
  val METHOD = "Method"
  val INCLUDE_IDENTIFIERS = "IncludeIdentifiers"
  val START_DATE = "StartDate"
  val START_TIME = "StartTime"
  val END_DATE = "EndDate"
  val END_TIME = "EndTime"
  val SYSTEM_CORR_ID = "SystemCorrelationID"
  val SYSTEM_TIMESTAMP = "SystemTimestamp"
  val MESSAGE_DETAILS = "MessageDetails"
  val GOV_TALK_DETAILS = "GovTalkDetails"
  val SENDER_DETAILS_UPSERT = s"<SenderDetails/>"

  def transactionIDUpserting(transID: Option[String]): String = s"<$TRANSACTION_ID>${transID.getOrElse("")}</$TRANSACTION_ID>"

  def responseEndpointInsert(responseEndpoint: String, attributeType: String, attributeValue: String): String =
    s"""<$RESPONSE_ENDPOINT $attributeType="$attributeValue">$responseEndpoint</$RESPONSE_ENDPOINT>"""

  val GOV_TALK_ERRORS = "GovTalkErrors"
  val ERROR = "Error"
  val RAISED_BY = "RaisedBy"
  val TYPE = "Type"
  val TYPE_FATAL = "fatal"
  val TYPE_BUSINESS = "business"
  val NUMBER = "Number"
  val errorNumberInsert = s"""<$NUMBER>3001</$NUMBER>"""
  val ERROR_RESPONSE_DEPARTMENT = "Department"
  val ERROR_NUMBER = "3001"
  val ERROR_RESPONSE_TEXT = "The submission of this document has failed due to departmental specific business logic in the Body tag."
  val TEXT = "Text"
  val SENDER_DETAILS = "SenderDetails"
  val ID_AUTHENTICATION = "IDAuthentication"
  val AUTHENTICATION = "Authentication"
  val PASSWORD = "Value"
  val MASK_PASSWORD = "**********"
  val EMAIL_ADDRESS = "EmailAddress"
  val PLACEHOLDER_EMAIL_ADDRESS = "placeholder@gateway.com"
  val GATEWAY_ADDITIONS = "GatewayAdditions"
  val KEYS = "Keys"
  val KEY = "Key"
  val BODY = "Body"
  val ANY = "*"
  val ERROR_RESPONSE = "ErrorResponse"
  val STATUS_REQUEST = "StatusRequest"
  val CHANNEL_ROUTING: String = "ChannelRouting"
  val CHANNEL: String = "Channel"
  val CHANNEL_URI: String = "URI"
  val PRODUCT: String = "Product"
  val PRODUCT_VERSION: String = "Version"
  val POLL = "poll"
  val SUBMISSION = "submission"
  val SUSPENDED_MESSAGE = "Temporarily Suspended"
  val DEFAULT_START_TIME = "00:00:00"
  val DEFAULT_END_TIME = "23:59:59"

  val submissionInstructions: Seq[XMLInstruction] = Seq[XMLInstruction](
    XMLExtract(Seq(GOV_TALK_MESSAGE), Map(XMLNS -> GOV_TALK_NAMESPACE)),
    //XMLNamespaceExtract(Seq(GOV_TALK_MESSAGE, BODY, ANY), true, validateNamespace),
    XMLExtract(Seq(GOV_TALK_MESSAGE, ENVELOPE_VERSION)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, MESSAGE_CLASS)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, QUALIFIER)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, FUNCTION)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, TRANSACTION_ID)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, CORRELATION_ID)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, RESPONSE_ENDPOINT)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, TRANSFORMATION)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, GATEWAY_TEST)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, MESSAGE_DETAILS, GATEWAY_TIMESTAMP)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, SENDER_DETAILS, ID_AUTHENTICATION, SENDER_ID)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, SENDER_DETAILS, ID_AUTHENTICATION, AUTHENTICATION, METHOD)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, SENDER_DETAILS, ID_AUTHENTICATION, AUTHENTICATION, PASSWORD)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, HEADER, SENDER_DETAILS, EMAIL_ADDRESS)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, GOV_TALK_DETAILS, KEYS, KEY)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, GOV_TALK_DETAILS, CHANNEL_ROUTING, CHANNEL, CHANNEL_URI)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, GOV_TALK_DETAILS, CHANNEL_ROUTING, CHANNEL, PRODUCT)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, GOV_TALK_DETAILS, CHANNEL_ROUTING, CHANNEL, PRODUCT_VERSION)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, INCLUDE_IDENTIFIERS)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, START_DATE)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, START_TIME)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, END_DATE)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, STATUS_REQUEST, END_TIME)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, INCLUDE_IDENTIFIERS)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, START_DATE)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, START_TIME)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, END_DATE)),
    XMLExtract(Seq(GOV_TALK_MESSAGE, BODY, END_TIME)),
    XMLUpdate(Seq(GOV_TALK_MESSAGE, HEADER, SENDER_DETAILS, ID_AUTHENTICATION, AUTHENTICATION, PASSWORD), updateElement(MASK_PASSWORD))
    //XMLValidate(Seq(GOV_TALK_MESSAGE), Seq(GOV_TALK_MESSAGE, BODY), validateSubmissionMessage)
  )

  def updateElement(s: String)(prefix: String): String = {
    s
  }

  it should "perform through all stages with config settings from TE" in {
    val testFile = scala.io.Source.fromFile("testSubmissions/submission24M.xml")
    val msg = try testFile.getLines().mkString("\n") finally testFile.close()
    val messages = ParserTestHelpers.getBrokenMessage(msg, 500)

    val as = ActorSystem("PerformanceTest")
    val am = ActorMaterializer()(as)

    val timeStarted = System.currentTimeMillis()

    val wholeSystem = Flow[ByteString]
      .via(MinimumChunk.parser(1024))
      .via(CompleteChunkStage.parser(Some(25000000)))
      .via(ParsingStage.parser(submissionInstructions, Some(1500), 0))
      .via(Flow[(ByteString, Set[XMLElement])].map(x => x._2))

    val source = Source.queue[ByteString](messages.size, OverflowStrategy.backpressure)
    val (queue, result) = source.via(wholeSystem).toMat(Sink.fold(Set.empty[XMLElement])(_ ++ _))(Keep.both).run()(am)
    messages.foreach(queue.offer _)
    queue.complete()

    assert(result.isReadyWithin(Span(15, Seconds)))
    whenReady(result){ elements => {
      assert(elements.size == 19)
      val duration = (System.currentTimeMillis() - timeStarted)
      info(s"Total time taken: ${duration}ms")
    }}
  }

  it should "ParsingStage be performant on large messages in several iterations" in {
    val testFile = scala.io.Source.fromFile("testSubmissions/submission24M.xml")
    val msg = try testFile.getLines().mkString("\n") finally testFile.close()

    val as = ActorSystem("PerformanceTest")
    val am = ActorMaterializer()(as)

    def runTest(): Future[Unit] = {
      val source = TestSource.probe[ParsingData](as)
      val chunk = ParsingStage.parser(submissionInstructions, Some(2000), 0)
      val (pub, sub) = source.via(chunk).toMat(Sink.fold(Set.empty[XMLElement])((sum, i) => sum ++ i._2))(Keep.both).run()(am)
      val messages = ParserTestHelpers.getBrokenMessage(msg, 500)
      var totalLength = 0
      messages.foreach { a =>
        totalLength += a.length
        pub.sendNext(ParsingData(a, Set.empty, totalLength))
      }
      pub.sendComplete()
      sub.map(_ => ())
    }

    val nRuns = 10
    val timeStarted = System.currentTimeMillis()

    (1 to nRuns).foreach(i => {
      whenReady(runTest()) { _ => ()}
    })
    
    val duration = System.currentTimeMillis() - timeStarted
    val perFile = duration / nRuns
    info(s"Total time taken: ${duration}ms")
    info(s"Time taken per file: ${perFile}ms")
    assert(perFile <= 15000)
  }

  ignore should "FastParsingStage be performant on large messages in several iterations" in {
    val testFile = scala.io.Source.fromFile("testSubmissions/submission24M.xml")
    val msg = try testFile.getLines().mkString("\n") finally testFile.close()
    //val input = ByteString.fromString(fileString)

    val as = ActorSystem("PerformanceTest")
    val am = ActorMaterializer()(as)
    val timeStarted = System.currentTimeMillis()
    runTest(10)

    def runTest(runCounter: Int) {
      if (runCounter <= 0) {
        val duration = (System.currentTimeMillis() - timeStarted) / 1000
        println("###Runtime: " + duration)
        return
      }
      val source = TestSource.probe[ByteString](as)
      val chunk = FastParsingStage.parser(submissionInstructions, Some(2000))

      val (pub, sub) = source.via(chunk).toMat(Sink.fold(Set.empty[XMLElement])((sum, i) => sum ++ i._2))(Keep.both).run()(am)

      val messages = ParserTestHelpers.getBrokenMessage(msg, 500)
      var totalLength = 0
      messages.foreach { data =>
        totalLength += data.length
        pub.sendNext(data)
      }
      pub.sendComplete()
      sub.onComplete {
        case Success(elems) =>
          println(">> " + elems)
          runTest(runCounter - 1)
        case Failure(ex) =>
          println("###Error: " + ex)
      }
    }
  }



}
