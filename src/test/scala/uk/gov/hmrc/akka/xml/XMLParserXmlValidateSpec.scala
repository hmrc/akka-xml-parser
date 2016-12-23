/*
 * Copyright 2016 HM Revenue & Customs
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

/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.akka.xml

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}

import scala.util.control.NoStackTrace

/**
  * Created by abhishek on 15/12/16.
  */
class XMLParserXmlValidateSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with Eventually
  with XMLParserFixtures {

  val f = fixtures

  import f._

  behavior of "AkkaXMLParser#parser"


  it should "validate successfully the specified data against a supplied function" in {
    val source = Source.single(ByteString("<xml><body><foo>test</foo><bar>test</bar></body></xml>"))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set.empty
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body></xml>"
    }
  }


  it should "fail validation when the specified data does not pass the supplied validation function" in {
    val source = Source.single(ByteString("<xml><body><foo>fail</foo><bar>fail</bar></body></xml>"))
    val error = new NoStackTrace {}
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(error)
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths)).failed) { r =>
      r shouldBe error
    }
  }

  it should "validate over multiple chunks" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>test</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set.empty
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body></xml>"
    }
  }

  it should "fail validation over multiple chunks" in {
    val source = Source(List(ByteString("<xml><bo"), ByteString("dy><foo>foo</fo"), ByteString("o><bar>test</bar></body></xml>")))
    val error = new NoStackTrace {}
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(error)
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body"), Seq("xml", "body"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths)).failed) { r =>
      r shouldBe error
    }
  }

  it should "validate with self closing tags" in {
    val source = Source.single(ByteString("<xml><foo/><bar>bar</bar></xml>"))
    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<xml><foo/><bar>bar</bar></xml>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml"), Seq("xml"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set.empty
    }
  }

  it should "fail validation if the start tag is not found" in {
    val source = Source.single(ByteString("<xml><body><bar>bar</bar></body></xml>"))
    val validatingFunction = (string: String) => None
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body", "foo"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths)).failed) { r =>
      r shouldBe an[XMLValidationException]
    }
  }

  it should "fail validation if the end tag is not found" in {
    val source = Source.single(ByteString("<xml><body><foo>foo</foo></body></xml>"))
    val validatingFunction = (string: String) => None
    val paths = Set[XMLInstruction](XMLValidate(Seq("xml", "body", "foo"), Seq("xml", "body", "bar"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths)).failed) { r =>
      r shouldBe an[XMLValidationException]
    }
  }

  it should "return a malformed status if the xml isn't properly closed off with an end tag" in {
    val source = Source.single(ByteString("<foo>bar"))
    val paths = Set[XMLInstruction](XMLExtract(XPath("foo")))
    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<foo>bar"
    }
  }

  it should "return a malformed status if the xml isn't properly closed off with an end tag (multiple chunks)" in {
    val source = Source(List(ByteString("<xml><foo>b"), ByteString("ar"), ByteString("</foo><hello>wor"), ByteString("ld</hello>")))

    val paths = Set[XMLInstruction](XMLExtract(XPath("xml/foo")))
    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set(
        XMLElement(List("xml", "foo"), Map.empty, Some("bar")),
        XMLElement(Nil, Map.empty, Some(AkkaXMLParser.MALFORMED_STATUS))
      )
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><foo>bar</foo><hello>world</hello>"
    }
  }


  it should "validate data in multiple chunks elements" in {

    val source = Source(List(ByteString(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope">
            <EnvelopeVersion>2.0</EnvelopeVersion>
            <Header>
                <MessageDetails>
                    <Class>HMRC-CT-CT600-TIL</Class>
                    <Qualifier>request</Qualifier>
                    <Function>submit</Function>
                    <TransactionID>FFFFA404</TransactionID>

                    <CorrelationID></CorrelationID>


                    <Transformation>XML</Transformation>



                </MessageDetails>
                <SenderDetails>
                    <IDAuthentication>
                        <SenderID>user1</SenderID>
                        <Authentication>
                            <Method>clear</Me"""),
      ByteString(
        """thod>
                                       <Role>Authenticate/Validate</Role>
                                       <Value>pass</Value>
                                   </Authentication>
                               </IDAuthentication>

                           </SenderDetails>
                       </Header>
                       <GovTalkDetails>
                          <Keys>
                               <Key Type="UTR">1234567890</Key>


                       </Keys>
                           <ChannelRouting>
                               <Channel>
                                   <URI>1352</URI>
                                   <Product>ASPIRE HMRC-VAT100-DEC</Product>
                                   <Version>1.0</Version>
                               </Channel>
                           </ChannelRouting>



                       </GovTalkDetails>
                       <Body>
                           <IRenvelope xmlns="http://www.govtalk.gov.uk/taxation/vat/vatdeclaration/2">
                               <IRheader>
                                   <Keys>
                                       <Key Type="VATRegNo">999989291</Key>
                                   </Keys>
                                   <PeriodID>2009-12</PeriodID>
                                   <PeriodStart>2009-10-01</PeriodStart>
                                   <PeriodEnd>2009-12-31</PeriodEnd>
                                   <IRmark Type="generic">GCxuuBCQsKaqKPk42Xgl6dPVg/M=</IRmark>
                                   <Sender>Individual</Sender>
                               </IRheader>
                               <VATDeclarationRequest>
                                   <VATDueOnOutpu"""),
      ByteString(
        """ts>100.00</VATDueOnOutputs>
                                   <VATDueOnECAcquisitions>100.00</VATDueOnECAcquisitions>
                                   <TotalVAT>200.00</TotalVAT>
                                   <VATReclaimedOnInputs>10.00</VATReclaimedOnInputs>
                                   <NetVAT>190.00</NetVAT>
                                   <NetSalesAndOutputs>1000</NetSalesAndOutputs>
                                   <NetPurchasesAndInputs>1000</NetPurchasesAndInputs>
                                   <NetECSupplies>1000</NetECSupplies>
                                   <NetECAcquisitions>1000</NetECAcquisitions>

                               </VATDeclarationRequest>
                           </IRenvelope>
                       </Body>
                   </GovTalkMessage>""")))

    val validatingFunction: String => Option[Throwable] = (string: String) => if (string == "<body><foo>test</foo><bar>test</bar></body>") None else Some(new NoStackTrace {})
    val paths = Set[XMLInstruction](
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
      XMLValidate(Seq("GovTalkMessage"), Seq("GovTalkMessage", "GovTalkDetails"), validatingFunction))

    whenReady(source.runWith(parseToXMLElements(paths))) { r =>
      r shouldBe Set.empty
    }
    whenReady(source.runWith(parseToByteString(paths))) { r =>
      r.utf8String shouldBe "<xml><body><foo>test</foo><bar>test</bar></body></xml>"
    }


  }


}
