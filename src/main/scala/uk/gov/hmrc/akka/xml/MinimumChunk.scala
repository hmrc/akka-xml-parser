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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.pekko.stream.{Attributes, FlowShape, Inlet, Outlet}
import org.apache.pekko.util.ByteString

/**
  * Created by abhishek on 01/03/17.
  */
@deprecated("Use FastParsingStage instead","akka-xml-parser 1.0.0")
object MinimumChunk {

  def parser(minimumChunkSize: Int):
  Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(minimumChunkSize))
  }

  private class StreamingXmlParser(minimumChunkSize: Int)
    extends GraphStage[FlowShape[ByteString, ByteString]]
      with StreamHelper
      with ParsingDataFunctions {

    val in: Inlet[ByteString] = Inlet("Chunking.in")
    val out: Outlet[ByteString] = Outlet("Chunking.out")
    override val shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        private var buffer = ByteString.empty

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            val elem = grab(in)
            buffer ++= elem
            emitChunk()
          }

          override def onUpstreamFinish(): Unit = {
            emit(out, buffer)
            completeStage()
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        private def emitChunk(): Unit = {
          if (buffer.size > minimumChunkSize) {
            push(out, buffer)
            buffer = ByteString.empty
          } else {
            pull(in)
          }
        }

      }
  }

}
