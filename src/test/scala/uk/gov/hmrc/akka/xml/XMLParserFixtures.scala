/*
 * Copyright 2018 HM Revenue & Customs
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

    def parseToXMLElements(instructions: Seq[XMLInstruction], maxSize: Option[Int] = None) = Flow[ByteString]
      .via(FastParsingStage.parser(instructions,maxSize))
      .via(flowXMLElements)
      .toMat(collectXMLElements)(Keep.right)

    def parseToXMLGroupElements(instructions: Seq[XMLInstruction],
                                parentNodes: Option[Seq[String]] = None): Sink[ByteString, Future[Set[XMLGroupElement]]] =
      Flow[ByteString]
        .via(ExtractStage.parser(instructions, parentNodes))
        .via(flowXMLGroupElements)
        .toMat(collectXMLGroupElements)(Keep.right)

    def parseToByteStringViaExtract(instructions: Seq[XMLInstruction],
                                parentNodes: Option[Seq[String]] = None) =
      Flow[ByteString]
        .via(ExtractStage.parser(instructions, parentNodes))
        .via(flowByteStringViaExtract)
        .toMat(collectByteString)(Keep.right)

    def parseToByteString(instructions: Seq[XMLInstruction],  insertPrologueIfNotPresent: Boolean = false, validationMaxSize: Option[Int] = None)
    = Flow[ByteString]
      .via(FastParsingStage.parser(instructions, validationMaxSize, insertPrologueIfNotPresent))
      .via(flowByteString)
      .toMat(collectByteString)(Keep.right)

    def parseToPrint(instructions: Seq[XMLInstruction]) = Flow[ByteString]
      .via(CompleteChunkStage.parser())
      .via(ParsingStage.parser(instructions))
      .via(flowByteStringPrint)
      .toMat(Sink.ignore)(Keep.right)

    def parseToByteStringViaTransform(instructions: Set[XMLInstruction]) = Flow[ByteString]
      .via(MinimumChunk.parser(15))
      .via(CompleteChunkStage.parser())
      .via(TransformStage.parser(instructions))
      .toMat(collectByteString)(Keep.right)

    def flowXMLElements = Flow[(ByteString, Set[XMLElement])].map(x => x._2)

    def flowXMLGroupElements = Flow[(ByteString, Set[XMLGroupElement])].map(x => x._2)

    def flowByteString = Flow[(ByteString, Set[XMLElement])].map(x => x._1)

    def flowByteStringViaExtract = Flow[(ByteString, Set[XMLGroupElement])].map(x => x._1)

    def flowByteStringPrint = Flow[(ByteString, Set[XMLElement])].map(x => x._1)

    def collectXMLElements: Sink[Set[XMLElement], Future[Set[XMLElement]]] =
      Sink.fold[Set[XMLElement], Set[XMLElement]](Set.empty)((a, b) => {
        a ++ b
      })

    def collectXMLGroupElements: Sink[Set[XMLGroupElement], Future[Set[XMLGroupElement]]] =
      Sink.fold[Set[XMLGroupElement], Set[XMLGroupElement]](Set.empty)((a, b) => {
        a ++ b
      })

    def collectByteString: Sink[ByteString, Future[ByteString]] =
      Sink.fold[ByteString, ByteString](ByteString(""))((a, b) => {
        a ++ b
      })

  }
}


object ParserTestHelpers {
  /**
    * Break up the xml to random peaces, so we could simulate a streaming data
    *
    * @return
    */
  def getBrokenMessage(message2Break: String, maxChunkSize: Int): List[ByteString] = {
    val rnd = scala.util.Random
    var sum = 0
    val lengths = new scala.collection.mutable.ArrayBuffer[Int]()
    while (sum < message2Break.length) { //Create a list of lengts we are gonna cut our string at
      val next = rnd.nextInt(maxChunkSize) + 1
      sum += next
      lengths += sum
    }
    lengths(lengths.length - 1) = message2Break.size
    var startAt = 0
    lengths.map { endAt =>
      val part = message2Break.substring(startAt, endAt)
      startAt = endAt
      ByteString(part.getBytes())
    }.toList
  }

  val sa100 =
    <GovTalkMessage xmlns="http://www.govtalk.gov.uk/CM/envelope">
      <EnvelopeVersion>2.0</EnvelopeVersion>
      <Header>
        <MessageDetails>
          <Class>HMRC-SA-SA100</Class> <Qualifier>request</Qualifier> <Function>submit</Function> <CorrelationID/> <Transformation>XML</Transformation> <GatewayTimestamp/>
        </MessageDetails> <SenderDetails>
        <IDAuthentication>
          <SenderID>agent1</SenderID> <Authentication>
          <Method>clear</Method> <Role>principal</Role> <Value>pass</Value>
        </Authentication>
        </IDAuthentication> <EmailAddress>aaa@bbb.co.uk</EmailAddress>
      </SenderDetails>
      </Header>
      <GovTalkDetails>
        <Keys>
          <Key Type="UTR">123123123</Key>
        </Keys> <TargetDetails>
        <Organisation>HMRC</Organisation>
      </TargetDetails> <ChannelRouting>
        <Channel>
          <URI>0016</URI> <Product>CCH Personal Tax</Product> <Version>2017.300.1710.12402</Version>
        </Channel>
      </ChannelRouting>
      </GovTalkDetails>
      <Body>
        <IRenvelope xmlns="http://www.govtalk.gov.uk/taxation/SA/SA100/16-17/2">
          <IRheader>
            <Keys>
              <Key Type="UTR">123123123</Key>
            </Keys> <PeriodEnd>2017-04-05</PeriodEnd> <DefaultCurrency>GBP</DefaultCurrency> <Manifest>
            <Contains>
              <Reference>
                <Namespace>http://www.govtalk.gov.uk/taxation/SA/SA100/16-17/2</Namespace> <SchemaVersion>2017-v2.0</SchemaVersion> <TopElementName>MTR</TopElementName>
              </Reference>
            </Contains>
          </Manifest> <IRmark Type="generic">zdWbRl7tYkSiz3T07/+rQTlVJ+8=</IRmark> <Sender>Agent</Sender>
          </IRheader> <MTR>
          <SA100>
            <YourPersonalDetails>
              <DateOfBirth>1999-12-12</DateOfBirth> <NationalInsuranceNumber>999999999</NationalInsuranceNumber> <TaxpayerStatus>U</TaxpayerStatus>
            </YourPersonalDetails>
            <Income>
              <UKInterestAndDividends>
                <UntaxedUKinterestEtc>3242.00</UntaxedUKinterestEtc>
              </UKInterestAndDividends> <StateBenefits>
              <AnnualStatePension>8633.00</AnnualStatePension> <OtherPensionsAndRetirementAnnuities>20713.00</OtherPensionsAndRetirementAnnuities> <TaxTakenOffPensionsAndRetirementAnnuities>3667.00</TaxTakenOffPensionsAndRetirementAnnuities>
            </StateBenefits>
            </Income>
            <TaxReliefs>
              <CharitableGiving>
                <GiftAidPaymentsMadeInYear>266.00</GiftAidPaymentsMadeInYear>
              </CharitableGiving>
            </TaxReliefs> <FinishingYourTaxReturn>
            <NotPaidEnough>
              <NonPAYEIncomeNotToBeCodedOut>yes</NonPAYEIncomeNotToBeCodedOut>
            </NotPaidEnough>
            <TaxAdviser>
              <TaxAdviser>AAAA</TaxAdviser> <TaxAdviserPhoneNumber>eeee</TaxAdviserPhoneNumber> <TaxAdviserAddress>
              <Line>ccccc</Line> <Line>sssss</Line> <Line>eeeee</Line> <PostCode>bbbb</PostCode>
            </TaxAdviserAddress>
            </TaxAdviser>
            <SigningYourForm>
              <OtherInformationSpace>asasdasd</OtherInformationSpace>
            </SigningYourForm>
          </FinishingYourTaxReturn>
          </SA100>
          <SA110>
            <SelfAssessment>
              <TotalTaxEtcDue>450.60</TotalTaxEtcDue>
            </SelfAssessment> <UnderpaidTax/>
          </SA110>
          <TaxpayerName>Mr AAA BBB</TaxpayerName> <Declaration>
            <AgentDeclaration>yes</AgentDeclaration>
          </Declaration>
        </MTR>
        </IRenvelope>
      </Body>
    </GovTalkMessage>


}
