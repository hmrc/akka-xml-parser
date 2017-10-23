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
object XmlEncodingYankerStage {

  val PROLOG_REGEX = """<\?xml(.*?)encoding="(.*)"(.*?)\?>"""   //<?xml version="1.0" encoding="ISO-8859-1"?>

  def parser():
  Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser())
  }

  private class StreamingXmlParser()
    extends GraphStage[FlowShape[ByteString, ByteString]]
      with StreamHelper
      with ParsingDataFunctions {

    val in: Inlet[ByteString] = Inlet("Prolog.in")
    val out: Outlet[ByteString] = Outlet("Prolog.out")
    override val shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        private var buffer = ByteString.empty
        private var prologFinished = false

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            val elem = grab(in)
            if (!prologFinished ) {  //Buffer only the beginning part of the stream to check the prolog
              buffer ++= elem
              if (buffer.length > 45) { //<?xml version="1.0" encoding="ISO-8859-1"?>
                val encodingRemoved = replceXmlEncoding(buffer)
                push(out,encodingRemoved)
                prologFinished = true
                buffer = ByteString.empty
              } else {  //Didn't arrive enough data to check prolog yet
                pull(in)
              }
            } else {
              push(out,elem)
            }

          }

          override def onUpstreamFinish(): Unit = {
            val encodingRemoved = replceXmlEncoding(buffer)
            emit(out, encodingRemoved)
            completeStage()
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        //Replace the encoding in the xml prolog, leave other parts untouched
        private def replceXmlEncoding(in:ByteString): ByteString = ByteString(in.utf8String.replaceAll(XmlEncodingYankerStage.PROLOG_REGEX,"<?xml$1encoding=\"UTF-8\"$3?>"))

      }
  }

}
