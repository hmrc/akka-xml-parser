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

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

/**
  * The FasterXML Altoo parser can only process UTF-8, but we might receive other encodings.
  * This stage does the unthinkable and replaces the encoding in the xml prolog without actually changing the encoding (this was the requirement)
  * Created by gabor dorcsinecz on 23/10/17.
  */
object XmlEncodingStage {

  val ENCODING_EXTRACTOR = """<\?xml.*?encoding="(.*?)".*""".r
  val PROLOG_REGEX = """<\?xml(.*?)encoding="(.*?)"(.*?)\?>""" //<?xml version="1.0" encoding="ISO-8859-1"?>
  val PROLOG_REGEX_ALL = """<\?xml(.*?)\?>"""
  val UTF8 = "UTF-8"

  def parser(convertEncodingTo: String):
  Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(convertEncodingTo))
  }

  private class StreamingXmlParser(replaceTo: String)
    extends GraphStage[FlowShape[ByteString, ByteString]]
      with StreamHelper
      with ParsingDataFunctions {

    val in: Inlet[ByteString] = Inlet("Prolog.in")
    val out: Outlet[ByteString] = Outlet("Prolog.out")
    override val shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        private var buffer = ByteString.empty
        private var prologFinished = false //
        private var incomingEncoding = "UTF-8"

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            val elem = grab(in)
            if (!prologFinished) { //Buffer only the beginning part of the stream to check the prolog
              buffer ++= elem
              if (buffer.length > 45) { //Do we have a prolog: <?xml version="1.0" encoding="ISO-8859-1"?>
                val encodingRemoved = replceXmlEncoding(buffer)
                push(out, encodingRemoved)
                prologFinished = true
                buffer = ByteString.empty
              } else { //Didn't arrive enough data to check prolog yet
                pull(in)
              }
            } else {  //The prolog already went through, all we need now is to convert the encoding
              push(out, convertEncoding(elem,incomingEncoding,replaceTo) )
            }

          }

          override def onUpstreamFinish(): Unit = {
            if (buffer.length > 0) { //This will only happen if didn't arrive enough data into the buffer to send the first batch through
              val encodingRemoved = replceXmlEncoding(buffer)
              emit(out, encodingRemoved)
            }
            completeStage()
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        //Replace the encoding in the xml prolog, leave other parts untouched
        private def replceXmlEncoding(in: ByteString): ByteString = {
          incomingEncoding = in.utf8String match {  //Extract the encoding from the
            case ENCODING_EXTRACTOR(enc) => enc
            case _ => "UTF-8"  //Either there is no xml prolog or the prolog doesn't contain an encoding attribute
          }

          (incomingEncoding == replaceTo) match {
            case true =>
              in
            case false =>
              val reEncoded = in.decodeString(incomingEncoding)
              val replaced = reEncoded.replaceAll(PROLOG_REGEX, "<?xml$1encoding=\"" + replaceTo + "\"$3?>")
              val encodingEnsured = replaced match {
                case ENCODING_EXTRACTOR(enc) =>   //If there is an encoding in the prolog then all is fine
                  replaced
                case _ => //There was no encoding attribute in the prolog, we put in one
                  reEncoded.replaceAll(PROLOG_REGEX_ALL, "<?xml$1 encoding=\"" + replaceTo + "\"?>")
              }
              ByteString.fromString(encodingEnsured, replaceTo)
          }
        }

        def convertEncoding(in: ByteString, encodingFrom: String, encodingTo: String): ByteString = {
          (encodingFrom == encodingTo) match {
            case true => in
            case false => ByteString.fromString(in.decodeString(encodingFrom), encodingTo)
          }
        }
      }

  }

}