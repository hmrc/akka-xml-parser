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
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.util.ByteString

import scala.concurrent.Future

/**
  * Created by abhishek on 09/12/16.
  */
trait XMLParserFixtures {

  def fixtures = new {

    implicit val system = ActorSystem("XMLParser")
    implicit val mat = ActorMaterializer()

    def parseToXMLElements(instructions: Set[XMLInstruction]) = Flow[ByteString]
      .via(AkkaXMLParser.parser(instructions))
      .via(flowXMLElements)
      .toMat(collectXMLElements)(Keep.right)

    def parseToByteString(instructions: Set[XMLInstruction]) = Flow[ByteString]
      .via(AkkaXMLParser.parser(instructions))
      .via(flowByteString)
      .toMat(collectByteString)(Keep.right)

    def parseToPrint(instructions: Set[XMLInstruction]) = Flow[ByteString]
      .via(AkkaXMLParser.parser(instructions))
        .via(flowByteStringPrint)
      .toMat(Sink.ignore)(Keep.right)

    def flowXMLElements = Flow[(ByteString, Set[XMLElement])].map(x => x._2)

    def flowByteString = Flow[(ByteString, Set[XMLElement])].map(x => x._1)

    def flowByteStringPrint = Flow[(ByteString, Set[XMLElement])].map(x => {
//      println("response bytestring ------------------------ : " + x._1.utf8String)
//      println("response element >>>>>>>>>>>> : " + x._2)
      x._1
    })

    def collectXMLElements: Sink[Set[XMLElement], Future[Set[XMLElement]]] =
      Sink.fold[Set[XMLElement], Set[XMLElement]](Set.empty)((a, b) => {
        a ++ b
      })

    def collectByteString: Sink[ByteString, Future[ByteString]] =
      Sink.fold[ByteString, ByteString](ByteString(""))((a, b) => {
        a ++ b
      })

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
    val GATEWAY_TEST = "GatewayTest"
    val GATEWAY_TIMESTAMP = "GatewayTimestamp"
    val SENDER_ID = "SenderID"
    val METHOD = "Method"
    val PASSWORD = "Value"
    val INCLUDE_IDENTIFIERS = "IncludeIdentifiers"
    val START_DATE = "StartDate"
    val START_TIME = "StartTime"
    val END_DATE = "EndDate"
    val END_TIME = "EndTime"
    val GEN_CORR_ID = "GenCorrID"
    val GEN_TIMESTAMP = "GenTimestamp"

    val MESSAGE_DETAILS = "MessageDetails"
    val GOV_TALK_DETAILS = "GovTalkDetails"

    val GOV_TALK_ERRORS = "GovTalkErrors"
    val ERROR = "Error"
    val RAISED_BY = "RaisedBy"
    val ERROR_RESPONSE_DEPARTMENT = "Department"
    val ERROR_RESPONSE_TEXT = "The submission of this document has failed due to departmental specific business logic in the Body tag."
    val TEXT = "Text"

    val SENDER_DETAILS = "SenderDetails"
    val ID_AUTHENTICATION = "IDAuthentication"
    val AUTHENTICATION = "Authentication"

    val KEYS = "Keys"
    val KEY = "Key"
    val BODY = "Body"
    val STATUS_REQUEST=  "StatusRequest"

    val CHANNEL_ROUTING: String = "ChannelRouting"
    val CHANNEL: String = "Channel"
    val CHANNEL_URI: String = "URI"
    val PRODUCT: String = "Product"
    val PRODUCT_VERSION: String = "Version"
  }

}
