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

///*
// * Copyright 2017 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.akka.xml
//
//import akka.NotUsed
//import akka.stream.scaladsl.Flow
//import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
//import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
//import akka.util.ByteString
//import com.fasterxml.aalto.stax.InputFactoryImpl
//import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader, WFCException}
//
//import scala.annotation.tailrec
//import scala.collection.mutable
//import scala.collection.mutable.ArrayBuffer
//import scala.util.{Failure, Success, Try}
//
///**
//  * Created by abhishek on 1/12/16.
//  */
//object AkkaXMLParser {
//  val MALFORMED_STATUS = "Malformed"
//  val STREAM_MAX_SIZE = "Stream max size"
//  val STREAM_IS_EMPTY = "Stream is empty"
//  val STREAM_SIZE_LESS_AND_BELOW_MIN = "Stream Size"
//  val STREAM_SIZE = "Stream Size"
//  val NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE = "No validation tags were found in first n bytes failure"
//  val VALIDATION_INSTRUCTION_FAILURE = "Validation instruction failure"
//  val PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE = "Not all of the xml validations / checks were done"
//  val XML_START_END_TAGS_MISMATCH = "Start and End tags mismatch. Element(s) - "
//
//  /**
//    * Parser Flow that takes a stream of ByteStrings and parses them to (ByteString, Set[XMLElement]).
//    */
//
//  def parser(instructions: Set[XMLInstruction], maxSize: Option[Int] = None,
//             validationMaxSize: Option[Int] = None, validationMaxSizeOffset: Int = 10000): Flow[ByteString, (ByteString, Set[XMLElement]), NotUsed] =
//
//  Flow.fromGraph(new StreamingXmlParser(instructions, maxSize, validationMaxSize, validationMaxSizeOffset))
//
//  private class StreamingXmlParser(instructions: Set[XMLInstruction], maxSize: Option[Int] = None,
//                                   validationMaxSize: Option[Int] = None, validationMaxSizeOffset: Int)
//    extends GraphStage[FlowShape[ByteString, (ByteString, Set[XMLElement])]]
//      with StreamHelper
//      with ParsingDataFunctions {
//    val in: Inlet[ByteString] = Inlet("XMLParser.in")
//    val out: Outlet[(ByteString, Set[XMLElement])] = Outlet("XMLParser.out")
//    override val shape: FlowShape[ByteString, (ByteString, Set[XMLElement])] = FlowShape(in, out)
//
//    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
//      new GraphStageLogic(shape) {
//
//        import javax.xml.stream.XMLStreamConstants
//
//        private val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
//        private val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = feeder.createAsyncFor(Array.empty)
//
//        setHandler(in, new InHandler {
//          override def onPush(): Unit = {
//            processStage(processOnPush)
//          }
//
//          override def onUpstreamFinish(): Unit = {
//            parser.getInputFeeder.endOfInput()
//            processStage(processOnUpstreamFinish)
//            completeStage()
//          }
//        })
//
//        setHandler(out, new OutHandler {
//          override def onPull(): Unit = {
//            pull(in)
//          }
//        })
//
//        var chunkOffset = 0
//        var chunk = Array[Byte]()
//        var totalReceivedLength = 0
//        var incompleteBytesLength = 0
//        var isCharacterBuffering = false
//        var inputChunkLength = 0
//        var previousChunkSize = 0
//        var streamBuffer = ArrayBuffer[Byte]()
//
//
//        val node = ArrayBuffer[String]()
//        //val nodesToProcess = mutable.Set[List[String]]()
//        val incompleteBytes = ArrayBuffer[Byte]()
//        val completedInstructions = mutable.Set[XMLInstruction]()
//        val xmlElements = mutable.Set[XMLElement]()
//        val validators = mutable.Map[XMLValidate, ArrayBuffer[Byte]]()
//        val bufferedText = new StringBuilder
//
//
//        private def emitStage(elementsToAdd: XMLElement*)(bytesToEmit: ByteString) = {
//          elementsToAdd.foreach(xmlElements.add)
//          emit(out, (bytesToEmit, getCompletedXMLElements(xmlElements).toSet))
//        }
//
//        def processOnPush() = {
//          chunk = grab(in).toArray
//          chunkOffset = 0
//          totalReceivedLength += chunk.length
//          inputChunkLength = chunk.length
//
//          (maxSize, validationMaxSize) match {
//            case _ if totalReceivedLength == 0 => Failure(EmptyStreamError())
//            case (Some(size), _) if totalReceivedLength > size => Failure(MaxSizeError())
//            case (_, Some(validationSize))
//              if totalReceivedLength > (validationSize + validationMaxSizeOffset) &&
//                instructions.collect { case e: XMLValidate => e }.exists(!completedInstructions.contains(_)) =>
//              Failure(new NoValidationTagsFoundWithinFirstNBytesException)
//            case _ =>
//              Try {
//                parser.getInputFeeder.feedInput(chunk, 0, chunk.length)
//                advanceParser()
//                push(out, (ByteString(streamBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
//                previousChunkSize = chunk.length
//                streamBuffer.clear()
//                if (incompleteBytes.nonEmpty) {
//                  streamBuffer ++= incompleteBytes
//                }
//                chunk = Array.empty[Byte]
//              }
//          }
//        }
//
//        def processOnUpstreamFinish(): Try[Unit] = {
//          for {
//            _ <- Try(advanceParser())
//            _ <- if ((instructions.count(x => x.isInstanceOf[XMLValidate]) > 0) &&
//              (completedInstructions.count(x => x.isInstanceOf[XMLValidate]) != instructions.count(x => x.isInstanceOf[XMLValidate]))) {
//              Failure(new IncompleteXMLValidationException)
//            }
//            else Success()
//          } yield {
//            if (node.nonEmpty)
//              xmlElements.add(XMLElement(Nil, Map(MALFORMED_STATUS ->
//                (XML_START_END_TAGS_MISMATCH + node.mkString(", "))), Some(MALFORMED_STATUS)))
//            if (streamBuffer.toArray.length > 0) {
//              emitStage(
//                XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
//              )(ByteString(incompleteBytes.toArray ++ chunk))
//              emit(out, (ByteString(streamBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
//            }
//            else {
//              emitStage(
//                XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
//              )(ByteString(Array.empty[Byte]))
//            }
//          }
//        }
//
//        def processStage(f: () => Try[Unit]) = {
//          f().recover {
//            case e: WFCException =>
//              emitStage(
//                XMLElement(Nil, Map(MALFORMED_STATUS -> e.getMessage), Some(MALFORMED_STATUS)),
//                XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
//              )(ByteString(incompleteBytes.toArray ++ chunk))
//              completeStage()
//            case e: MaxSizeError =>
//              emitStage(
//                XMLElement(Nil, Map.empty, Some(STREAM_MAX_SIZE)),
//                XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
//              )(ByteString(incompleteBytes.toArray ++ chunk))
//              completeStage()
//            case e: EmptyStreamError =>
//              emitStage(
//                XMLElement(Nil, Map.empty, Some(STREAM_IS_EMPTY)),
//                XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
//              )(ByteString(Array.empty[Byte]))
//              completeStage()
//            case e: NoValidationTagsFoundWithinFirstNBytesException =>
//              emitStage(
//                XMLElement(Nil, Map(NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE -> ""),
//                  Some(NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE)),
//                XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
//              )(ByteString(incompleteBytes.toArray ++ chunk))
//              completeStage()
//            case e: IncompleteXMLValidationException =>
//              emitStage(
//                XMLElement(Nil, Map(PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE -> ""),
//                  Some(PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE)),
//                XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
//              )(ByteString(incompleteBytes.toArray ++ chunk))
//              completeStage()
//            case e: ParserValidationError =>
//              emitStage(
//                XMLElement(Nil, Map(VALIDATION_INSTRUCTION_FAILURE -> e.toString), Some(VALIDATION_INSTRUCTION_FAILURE)),
//                XMLElement(Nil, Map(STREAM_SIZE -> totalReceivedLength.toString), Some(STREAM_SIZE))
//              )(ByteString(incompleteBytes.toArray ++ chunk))
//              completeStage()
//            case e: Throwable =>
//              throw e
//          }
//        }
//
//        @tailrec private def advanceParser(): Unit = {
//          if (parser.hasNext) {
//            val event = parser.next()
//
//            val (start, end) = getBounds(parser)
//            event match {
//              case AsyncXMLStreamReader.EVENT_INCOMPLETE =>
//                if (chunk.length > 0) {
//                  val lastCompleteElementInChunk = chunk.slice(0, start)
//                  incompleteBytes ++= chunk.slice(start, chunk.length)
//
//                  if (lastCompleteElementInChunk.length == 0 && (end - start) > chunk.length)
//                    streamBuffer.clear()
//                  else
//                    streamBuffer ++= lastCompleteElementInChunk.slice(chunkOffset, lastCompleteElementInChunk.length)
//
//
//                  incompleteBytesLength = incompleteBytes.length
//                }
//
//              case XMLStreamConstants.START_ELEMENT =>
//                node += parser.getLocalName
//                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => e match {
//                  case e@XMLExtract(`node`, _) if getPredicateMatch(parser, e.attributes).nonEmpty || e.attributes.isEmpty =>
//                    val keys = getPredicateMatch(parser, e.attributes)
//                    val ele = XMLElement(e.xPath, keys, None)
//                    xmlElements.add(ele)
//
//                  case e: XMLUpdate if e.xPath == node.slice(0, e.xPath.length) =>
//                    e.xPath match {
//                      case path if path == node.toList =>
//                        val input = getUpdatedElement(e.xPath, e.attributes, e.value)(parser).getBytes
//                        streamBuffer = if (e.isUpsert) getStreamBuffer
//                        else getStreamBuffer.slice(0, streamBuffer.length - incompleteBytesLength)
//                        streamBuffer ++= extractBytes(chunk, chunkOffset, start, input)
//                        incompleteBytesLength = 0
//                        chunkOffset = end
//                      case _ =>
//                        chunkOffset = end
//                    }
//
//                  case e: XMLValidate if e.start == node.slice(0, e.start.length) =>
//                    val newBytes = (streamBuffer.toArray ++ chunk).slice(start + incompleteBytesLength, end + incompleteBytesLength)
//                    if (!parser.isEmptyElement) {
//                      val ele = validators.get(e) match {
//                        case Some(x) => (e, x ++ newBytes)
//                        case None => (e, ArrayBuffer.empty ++= newBytes)
//                      }
//                      validators += ele
//                    }
//
//                  case e: XMLDelete if e.xPath == node.slice(0, e.xPath.length) =>
//                    streamBuffer = getStreamBuffer.slice(0, streamBuffer.length - incompleteBytesLength)
//                    streamBuffer ++= extractBytes(chunk, chunkOffset, start)
//                    chunkOffset = end
//                    incompleteBytesLength = 0
//
//                  case x =>
//                })
//                incompleteBytes.clear()
//                if (parser.hasNext) advanceParser()
//
//              case XMLStreamConstants.END_ELEMENT =>
//                isCharacterBuffering = false
//                instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => {
//                  e match {
//                    case e@XMLExtract(`node`, _) =>
//                      update(xmlElements, node, Some(bufferedText.toString()))
//
//                    case e: XMLUpdate if e.xPath.dropRight(1) == node && e.isUpsert =>
//                      val input = getUpdatedElement(e.xPath, e.attributes, e.value)(parser).getBytes
//                      streamBuffer = getStreamBuffer
//                      streamBuffer ++= extractBytes(chunk, chunkOffset, start, input)
//                      completedInstructions += e
//                      chunkOffset = start
//
//                    case e: XMLUpdate if e.xPath == node.slice(0, e.xPath.length) =>
//                      streamBuffer = getStreamBuffer.slice(incompleteBytesLength, streamBuffer.length)
//                      e.xPath match {
//                        case path if path == node.toList =>
//                          completedInstructions += e
//                        case _ =>
//                      }
//                      chunkOffset = end
//
//                    case e: XMLValidate if e.start == node.slice(0, e.start.length) =>
//                      val newBytes = (streamBuffer.toArray ++ chunk).slice(start + incompleteBytesLength, end + incompleteBytesLength)
//                      val ele = validators.get(e) match {
//                        case Some(x) => (e, x ++= newBytes)
//                        case None => throw new IncompleteXMLValidationException
//                      }
//                      validators += ele
//                      validators.foreach {
//                        case (s@XMLValidate(_, `node`, f), testData) =>
//                          f(new String(testData.toArray)).map(throw _)
//                          completedInstructions += e
//
//                        case x =>
//                      }
//
//                    case e: XMLDelete if e.xPath == node.slice(0, e.xPath.length) =>
//                      streamBuffer = getStreamBuffer.slice(incompleteBytesLength, streamBuffer.length)
//                      chunkOffset = end
//                      incompleteBytesLength = 0
//                    case x =>
//                  }
//                })
//                incompleteBytes.clear()
//                bufferedText.clear()
//                node -= parser.getLocalName
//                if (parser.hasNext) advanceParser()
//
//              case XMLStreamConstants.CHARACTERS =>
//                instructions.foreach(f = (e: XMLInstruction) => {
//                  e match {
//                    case e@XMLExtract(`node`, _) =>
//                      val t = parser.getText()
//                      if (t.trim.length > 0) {
//                        isCharacterBuffering = true
//                        bufferedText.append(t)
//                      }
//                    case e: XMLUpdate if e.xPath == node.slice(0, e.xPath.length) =>
//                      chunkOffset += (end - start)
//
//                    case e: XMLValidate if e.start == node.slice(0, e.start.length) =>
//                      val newBytes = (streamBuffer.toArray ++ chunk).slice(start + incompleteBytesLength, end + incompleteBytesLength)
//                      val ele = validators.get(e) match {
//                        case Some(x) => (e, x ++ newBytes)
//                        case None => (e, ArrayBuffer.empty ++= newBytes)
//                      }
//                      validators += ele
//
//                    case e: XMLDelete if e.xPath == node.slice(0, e.xPath.length) =>
//                      chunkOffset += (end - start)
//
//                    case _ =>
//                  }
//                })
//                if (parser.hasNext) advanceParser()
//
//              case x =>
//                if (parser.hasNext) advanceParser()
//            }
//          }
//        }
//
//        private def getStreamBuffer = {
//          //          if (streamBuffer.length != previousChunkSize)
//          //            streamBuffer.slice(0, streamBuffer.length - incompleteBytesLength)
//          //          else streamBuffer
//          streamBuffer
//        }
//
//        private def getBounds(implicit reader: AsyncXMLStreamReader[AsyncByteArrayFeeder]): (Int, Int) = {
//          val start = reader.getLocationInfo.getStartingByteOffset.toInt
//          (
//            if (start == 1) 0 else start - (totalReceivedLength - inputChunkLength),
//            reader.getLocationInfo.getEndingByteOffset.toInt - (totalReceivedLength - inputChunkLength)
//            )
//        }
//      }
//
//  }
//
//}
