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

    def parseToXMLElements(instructions: Set[XMLInstruction], maxSize: Option[Int] = None, validationMaxSize: Option[Int] = None) = Flow[ByteString]
      .via(AkkaXMLParser.parser(instructions, maxSize, validationMaxSize, 10))
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
      println("bytestring--" + x._1.utf8String)
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

  }
}
