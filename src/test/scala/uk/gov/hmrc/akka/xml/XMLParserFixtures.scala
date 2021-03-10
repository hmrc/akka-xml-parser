/*
 * Copyright 2021 HM Revenue & Customs
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
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future

/**
  * Created by abhishek on 09/12/16.
  */
trait XMLParserFixtures {

  def fixtures = new {

    val testConf: Config = ConfigFactory.parseString("""
    akka.loglevel = DEBUG
    akka.stream.materializer.debug-logging = on
    akka.stream.materializer.debug.fuzzing-mode = on
    """)
    implicit val system = ActorSystem("XMLParser",testConf)
    implicit val mat = ActorMaterializer()

    def parseToXMLElements(instructions: Seq[XMLInstruction], maxSize: Option[Int] = None,
                           validationMaxSize: Option[Int] = None) = Flow[ByteString]
      .via(MinimumChunk.parser(15))
      .via(CompleteChunkStage.parser(maxSize))
      .via(ParsingStage.parser(instructions, validationMaxSize, 10))
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

    def parseToByteString(instructions: Seq[XMLInstruction],  insertPrologueIfNotPresent: Boolean = false)
    = Flow[ByteString]
      .via(MinimumChunk.parser(15))
      .via(CompleteChunkStage.parser(None, insertPrologueIfNotPresent))
      .via(ParsingStage.parser(instructions))
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
