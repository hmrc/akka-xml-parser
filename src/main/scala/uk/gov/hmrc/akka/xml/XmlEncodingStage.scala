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

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import java.util.regex.Pattern

/**
  * The FasterXML Altoo parser can only process UTF-8, but we might receive other encodings.
  * This stage will replace the encoding in the xml prolog and it will then re-encode the whole xml in the given encoding
  * Created by gabor dorcsinecz on 23/10/17.
  */
object XmlEncodingStage {

  val ENCODING_EXTRACTOR = Pattern.compile("""<\?xml.*?encoding="(.*?)".*""")
  val PROLOG_REGEX = """<\?xml(.*?)encoding="(.*?)"(.*?)\?>""" //<?xml version="1.0" encoding="ISO-8859-1"?>
  val PROLOG_REGEX_ALL = """<\?xml(.*?)\?>"""  //Extract the entire body of the prolog
  val UTF8 = "UTF-8"
  val PROLOG_MAX_SIZE = 50

  def parser(outgoinEncoding: String):
  Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(outgoinEncoding))
  }

  private class StreamingXmlParser(outgoinEncoding: String)
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
              if (buffer.length > PROLOG_MAX_SIZE) { //Do we have a prolog: <?xml version="1.0" encoding="ISO-8859-1"?>
                val encodingRemoved = replaceXmlEncoding(buffer)
                push(out, encodingRemoved)
                prologFinished = true
                buffer = ByteString.empty
              } else { //Didn't arrive enough data to check prolog yet
                pull(in)
              }
            } else { //The prolog already went through, all we need now is to convert the encoding
              push(out, convertEncoding(elem, incomingEncoding, outgoinEncoding))
            }

          }

          override def onUpstreamFinish(): Unit = {
            if (buffer.length > 0) { //This will only happen if didn't arrive enough data into the buffer to send the first batch through
              val encodingRemoved = replaceXmlEncoding(buffer)
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
        private def replaceXmlEncoding(incomingBytes: ByteString): ByteString = {
          val theBeginning = incomingBytes.slice(0, PROLOG_MAX_SIZE).utf8String
          val encodingMatcher = ENCODING_EXTRACTOR.matcher(theBeginning)
          if (encodingMatcher.find()) { //For some misterious reason the scala case class extraction way did work in the testcases, but not in production
            incomingEncoding = encodingMatcher.group(1) //Extract the encoding from the prolog
          }

          if (incomingEncoding == outgoinEncoding) {
            incomingBytes //If the target encoding is the same as the incoming encoding, then we do nothing
          } else {  //We need to re-encode
            val reEncoded = incomingBytes.decodeString(incomingEncoding) //Decode the incoming ByteString according to the encoding in the prolog
          val prologReplaced = reEncoded.replaceAll(PROLOG_REGEX, "<?xml$1encoding=\"" + outgoinEncoding + "\"$3?>")
            if (ENCODING_EXTRACTOR.matcher(prologReplaced).find()) { //If there is an encoding in the prolog then all is fine
              ByteString.fromString(prologReplaced, outgoinEncoding) //Re-encode the xml according to the desired encoding
            } else { //There was no encoding attribute in the prolog, we put in one
              val encodingInserted = reEncoded.replaceAll(PROLOG_REGEX_ALL, "<?xml$1 encoding=\"" + outgoinEncoding + "\"?>")
              ByteString.fromString(encodingInserted, outgoinEncoding) //Re-encode the xml according to the desired encoding
            }
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
