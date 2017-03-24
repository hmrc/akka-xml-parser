package uk.gov.hmrc.akka.xml

import javax.xml.stream.XMLStreamConstants

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.fasterxml.aalto.stax.InputFactoryImpl
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * Created by abhishek on 23/03/17.
  */
object TransformStage {
  def parser(instructions: Set[XMLInstruction])
  : Flow[ParsingData, (ByteString, Set[XMLElement]), NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(instructions))
  }

  private class StreamingXmlParser(instructions: Set[XMLInstruction])
    extends GraphStage[FlowShape[ByteString, ByteString]]
      with StreamHelper
      with ParsingDataFunctions {
    val in: Inlet[ByteString] = Inlet("Transform.in")
    val out: Outlet[ByteString] = Outlet("Transform.out")
    override val shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        private var buffer = ByteString.empty

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            parsingData = grab(in)
            parser.getInputFeeder.feedInput(parsingData.toArray, 0, parsingData.length)
            advanceParser()
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

        }

        val node = ArrayBuffer[String]()
        val streamBuffer = ArrayBuffer[Byte]()
        var parsingData = ByteString("")
        var validating = true
        var chunkOffset = 0


        private val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
        private val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = feeder.createAsyncFor(Array.empty)

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            val (start, end) = getBounds(parser)
            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                streamBuffer ++= parsingData.slice(chunkOffset, parsingData.length)

              case XMLStreamConstants.START_ELEMENT =>
                node += parser.getLocalName
                instructions.foreach(f = (e: XMLInstruction) => e match {
                  case e: XMLTransform if e.startPath == node.slice(0, e.startPath.length) && validating =>
                    val newBytes = parsingData.slice(start, end)
                    streamBuffer ++= newBytes

                  case x =>
                })
                advanceParser()

              case XMLStreamConstants.END_ELEMENT =>
                instructions.foreach(f = (e: XMLInstruction) => {
                  e match {
                    case e: XMLTransform if e.startPath == node.slice(0, e.startPath.length) && validating =>
                      val newBytes = parsingData.slice(start, end)
                      streamBuffer ++= newBytes
                      if (node == e.endPath) {
                        val transformedData: String = e.f(new String(streamBuffer.toArray))
                        validating = false
                        emit(out, ByteString(transformedData))
                      }

                    case x =>
                  }
                })
                node -= parser.getLocalName
                advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                instructions.foreach(f = (e: XMLInstruction) => {
                  e match {
                    case e: XMLTransform if e.startPath == node.slice(0, e.startPath.length) && validating =>
                      val newBytes = parsingData.slice(start, end)
                      streamBuffer ++= newBytes

                    case _ =>
                  }
                })
                advanceParser()

              case _ =>
                advanceParser()
            }
          }
        }

        private def getBounds(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): (Int, Int) = {
          val start = reader.getLocationInfo.getStartingByteOffset.toInt
          (
            if (start == 1) 0 else start - (parsingData.totalProcessedLength - parsingData.data.length),
            reader.getLocationInfo.getEndingByteOffset.toInt - (parsingData.totalProcessedLength - parsingData.data.length)
            )
        }

      }
  }

}
