package uk.gov.hmrc.akka.xml

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.fasterxml.aalto.stax.InputFactoryImpl
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader, WFCException}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Try}

/**
  * Created by abhishek on 28/02/17.
  */
object CompleteChunkStage {
  val MALFORMED_STATUS = "Malformed"
  val STREAM_MAX_SIZE = "Stream max size"
  val STREAM_IS_EMPTY = "Stream is empty"
  val STREAM_SIZE_LESS_AND_BELOW_MIN = "Stream Size"
  val STREAM_SIZE = "Stream Size"
  val NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE = "No validation tags were found in first n bytes failure"
  val VALIDATION_INSTRUCTION_FAILURE = "Validation instruction failure"
  val PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE = "Not all of the xml validations / checks were done"
  val XML_START_END_TAGS_MISMATCH = "Start and End tags mismatch. Element(s) - "

  def parser(maxSize: Option[Int] = None):
  Flow[ByteString, ParsingData, NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(maxSize))
  }

  private class StreamingXmlParser(maxSize: Option[Int] = None)
    extends GraphStage[FlowShape[ByteString, ParsingData]]
      with StreamHelper
      with ParsingDataFunctions {

    val in: Inlet[ByteString] = Inlet("XMLParser.in")
    val out: Outlet[ParsingData] = Outlet("XMLParser.out")
    override val shape: FlowShape[ByteString, ParsingData] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        private val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
        private val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = feeder.createAsyncFor(Array.empty)

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            processStage(processOnPush)
          }

          override def onUpstreamFinish(): Unit = {
            parser.getInputFeeder.endOfInput()
            //processStage(processOnUpstreamFinish)
            completeStage()
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        def processOnPush() = {
          chunk = grab(in).toArray
          totalProcessedLength += chunk.length
          (maxSize) match {
            case _ if totalProcessedLength == 0 => Failure(EmptyStreamError())
            case Some(size) if totalProcessedLength > size => Failure(MaxSizeError())
            case _ =>
              Try {
                parser.getInputFeeder.feedInput(chunk, 0, chunk.length)
                advanceParser()
                totalProcessedLength -= incompleteBytes.length
                push(out, ParsingData(ByteString(streamBuffer.toArray), Set.empty, totalProcessedLength))
                streamBuffer.clear()
                streamBuffer ++= incompleteBytes
                incompleteBytes.clear()
                chunk = Array.empty[Byte]
              }
          }
        }

        private def emitStage(elementsToAdd: XMLElement*)(bytesToEmit: ByteString) = {
          emit(out, ParsingData(bytesToEmit, elementsToAdd.toSet, totalProcessedLength))
        }

        def processStage(f: () => Try[Unit]) = {
          f().recover {
            case e: WFCException =>
              emitStage(
                XMLElement(Nil, Map(MALFORMED_STATUS -> e.getMessage), Some(MALFORMED_STATUS)),
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
              )(ByteString(incompleteBytes.toArray ++ chunk))
              completeStage()
            case e: MaxSizeError =>
              emitStage(
                XMLElement(Nil, Map.empty, Some(STREAM_MAX_SIZE)),
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
              )(ByteString(incompleteBytes.toArray ++ chunk))
              completeStage()
            case e: EmptyStreamError =>
              emitStage(
                XMLElement(Nil, Map.empty, Some(STREAM_IS_EMPTY)),
                XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
              )(ByteString(Array.empty[Byte]))
              completeStage()
            case e: Throwable =>
              throw e
          }
        }

        var chunk = Array[Byte]()
        var totalProcessedLength = 0
        val streamBuffer = ArrayBuffer[Byte]()
        val incompleteBytes = ArrayBuffer[Byte]()

        @tailrec private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            val (start, end) = getBounds(parser)
            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
                streamBuffer ++= chunk.slice(0, start)
                incompleteBytes ++= chunk.slice(start, chunk.length)
              case x =>
                if (parser.hasNext) advanceParser()
            }
          }
        }

        private def getBounds(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): (Int, Int) = {
          val start = reader.getLocationInfo.getStartingByteOffset.toInt
          (
            if (start == 1) 0 else start - (totalProcessedLength - chunk.length),
            reader.getLocationInfo.getEndingByteOffset.toInt - (totalProcessedLength - chunk.length)
            )
        }
      }
  }

}