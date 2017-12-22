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
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLInputFactory, AsyncXMLStreamReader, WFCException}
import com.fasterxml.aalto.stax.InputFactoryImpl

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

/**
  * Parse an xml document as it is flowing through the system. By parsing we mean extracting/updating/deleting/validating certain elements
  * Parsing a big document is very resource intensive, so we provide the option to only parse the beginning part, and then
  * leave the rest of the document unparsed, use XMLStopParsing for this purpose
  * Created by Gabor Dorcsinecz on 29/11/17.
  */
object FastParsingStage {
  val MALFORMED_STATUS = "Malformed"
  val STREAM_MAX_SIZE = "Stream max size"
  val STREAM_IS_EMPTY = "Stream is empty"
  val STREAM_SIZE_LESS_AND_BELOW_MIN = "Stream Size"
  val STREAM_SIZE = "Stream Size"
  val NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE = "No validation tags were found in first n bytes failure"
  val VALIDATION_INSTRUCTION_FAILURE = "Validation instruction failure"
  val PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE = "Not all of the xml validations / checks were done"
  val XML_START_END_TAGS_MISMATCH = "Start and End tags mismatch. Element(s) - "
  val OPENING_CHEVRON = '<'.toByte
  val XMLPROLOGUE_START = "<?xml version"
  val XMLPROLOGUE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
   

  /**
    * Create a streaming parser that can extract/update/delete/insert xml elements into our xml data stream while flowing through the system
    * @param instructions
    * @param maxPackageSize
    * @return
    */
  def parser(instructions: Seq[XMLInstruction], maxPackageSize: Option[Int] = None, insertPrologueIfNotPresent:Boolean = false)
  : Flow[ByteString, (ByteString, Set[XMLElement]), NotUsed] = {
    Flow.fromGraph(new StreamingXmlParser(instructions, maxPackageSize, insertPrologueIfNotPresent))
  }

  private class StreamingXmlParser(instructions: Seq[XMLInstruction], maxPackageSize: Option[Int] = None, insertPrologueIfNotPresent:Boolean = false)
    extends GraphStage[FlowShape[ByteString, (ByteString, Set[XMLElement])]]
      with StreamHelper
      with ParsingDataFunctions {
    val in: Inlet[ByteString] = Inlet("XMLParser.in")
    val out: Outlet[(ByteString, Set[XMLElement])] = Outlet("XMLParser.out")

    override val shape: FlowShape[ByteString, (ByteString, Set[XMLElement])] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        import javax.xml.stream.XMLStreamConstants

        private val feeder: AsyncXMLInputFactory = new InputFactoryImpl()
        private val parser: AsyncXMLStreamReader[AsyncByteArrayFeeder] = feeder.createAsyncFor(Array.empty)
        var parsingData = ByteString.empty //Store the data here for parsing
        var isCharacterBuffering = false
        var chunkOffset = 0 //A pointer in the current data chunk
        var continueParsing = true //When we reached our parsing length/target we don't want to parse anymore
        var elementBlockExtracting: Boolean = false
        var totalProcessedLength = 0 //How many bytes did we send out which almost always equals the length we received. We remove the BOM
        var isFirstChunk = true //In case of the first chunk we try to remove the BOM
        var xmlRootOpeningTag: Option[String] = None  //root opening xml tag in the incoming document
        var lastChunk = ByteString.empty   //We may or may not xml parse the last bytes of the document. But in the end we can check the wether the closing tag is the same as the opening
        var numberOfUnclosedXMLTags = 0  //For every xml tag opening we add 1, for every closing we deduct 1, so in a valid document this should be 0

        val node = ArrayBuffer[String]()
        val completedInstructions = ArrayBuffer[XMLInstruction]() //Gather the already parsed (e.g. extracted) stuff here
        val xmlElements = mutable.Set[XMLElement]() //Temporary storage of half parsed elements, when they are fully parsed will be moved to completedInstructions
        val bufferedText = new StringBuilder //Buffer for xml Text nodes
        val streamBuffer = ArrayBuffer[Byte]()
        val incompleteBytes = ArrayBuffer[Byte]() //xml tags or text are broken at package boudaries. We store the here to retry at the next iteration
        val elementBlock = new StringBuilder
        val validators = mutable.Map[XMLValidate, ArrayBuffer[Byte]]()
        val xmlStopParsing = instructions.collect { case sp: XMLStopParsing => sp }.headOption
        val maxParsingSize = xmlStopParsing.flatMap(a => a.maxParsingSize) //if no maximum validation size was given we will parse the whole document
        val lastChunkBufferSize = 32  //How much data should we store in the lastChunk buffer

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            processPush()
          }

          override def onUpstreamFinish(): Unit = {
            parser.getInputFeeder.endOfInput()
            processUpstreamFinish()
            completeStage()
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        def processPush(): Unit = {
          var incomingData = grab(in) //This is a var for a reason. We don't want to copy to another variable every time, when we only change it very few times
          updateLastChunk(incomingData)
          if (isFirstChunk && incomingData.length > 0) { //Remove any Byte Order Mark from the file
            isFirstChunk = false
            val openingChevronAt = incomingData.indexOf(OPENING_CHEVRON)
            if (openingChevronAt > 0) { //This file stream has a BOM (Byte Order Mark) at the beginning or it is not xml
              incomingData = incomingData.drop(openingChevronAt)
            }
  	        if(insertPrologueIfNotPresent && !incomingData.utf8String.contains(XMLPROLOGUE_START)) {
              incomingData = ByteString(XMLPROLOGUE) ++ incomingData
            } 
			
          }
          chunkOffset = 0
          Try {
            totalProcessedLength += incomingData.length //incompleteBytes were already added to the total length earlier
            if (continueParsing) { //We want to parse the beginning of an xml, and not the rest.
              parser.getInputFeeder.feedInput(incomingData.toArray, 0, incomingData.length)
              parsingData = ByteString(incompleteBytes.toArray) ++ incomingData
              incompleteBytes.clear()
              advanceParser()
              push(out, (ByteString(streamBuffer.toArray), getCompletedXMLElements(xmlElements).toSet))
              streamBuffer.clear()

              val isMaxPasingSizeReached = totalProcessedLength > maxParsingSize.getOrElse(Int.MaxValue)
              if (isMaxPasingSizeReached) { //Stop parsing when the max parsing size was reached
                continueParsing = false
                parser.getInputFeeder.endOfInput()
                if (instructions.collect { case v: XMLValidate => v }.exists(!completedInstructions.contains(_))) {
                  throw new NoValidationTagsFoundWithinFirstNBytesException
                }
              }

              if (totalProcessedLength == 0) { //Handle empty payload
                throw new EmptyStreamError()
              }
              if (totalProcessedLength > maxPackageSize.getOrElse(Int.MaxValue)) { //Don't let users/hackers overload the system
                throw new MaxSizeError()
              }
            } else { //We parsed the beginning of the xml already, so let's just push the rest of the data through
              if (incompleteBytes.length > 0) { //if we have incompleteBytes we must send them out too, which can happen just after parsing was finished
                incomingData = ByteString(incompleteBytes.toArray) ++ incomingData
                incompleteBytes.clear()
              }
              push(out, (incomingData, Set.empty[XMLElement]))
            }
          }.recover(recoverFromErrors)
        }

        def processUpstreamFinish(): Unit = {
          Try {
            if (continueParsing) { //Only parse the remaining bytes if they are required
              advanceParser()
            }
            if (totalProcessedLength == 0)    //Handle empty payload
                throw new EmptyStreamError()

			val requestedValidations = instructions.count(_.isInstanceOf[XMLValidate])
            val completedValidations = completedInstructions.count(_.isInstanceOf[XMLValidate])
            if (requestedValidations > 0 && completedValidations != requestedValidations) { //Did we complete all given validation istructions
              throw new IncompleteXMLValidationException()
            }

            if (endTagsMismatch)
              throw new WFCException(XML_START_END_TAGS_MISMATCH, parser.getLocation)

            val xmlRootEndTag = Some(extractEndTag(lastChunk.utf8String))
            if (xmlRootEndTag!= xmlRootOpeningTag ) { //If we interrupted parsing, we still extract the closing xml tag and check if the document was not cut in half
              throw new WFCException(XML_START_END_TAGS_MISMATCH, parser.getLocation)
            }
          }.recover(recoverFromErrors)
          emitStage()
        }

        private def recoverFromErrors(): PartialFunction[Throwable, Unit] = {
          case e: WFCException =>
            incompleteBytes.prependAll(parsingData.toArray)
            emitStage(XMLElement(Nil, Map(MALFORMED_STATUS -> e.getMessage), Some(MALFORMED_STATUS)))
            completeStage()
          case e: NoValidationTagsFoundWithinFirstNBytesException =>
            emitStage(XMLElement(Nil, Map(NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE -> ""), Some(NO_VALIDATION_TAGS_FOUND_IN_FIRST_N_BYTES_FAILURE)))
            completeStage()
          case e: IncompleteXMLValidationException =>
            emitStage(XMLElement(Nil, Map(PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE -> ""), Some(PARTIAL_OR_NO_VALIDATIONS_DONE_FAILURE)))
            completeStage()
          case e: MaxSizeError =>
            emitStage(XMLElement(Nil, Map.empty, Some(STREAM_MAX_SIZE)))
            completeStage()
          case e: EmptyStreamError =>
            emitStage(XMLElement(Nil, Map.empty, Some(STREAM_IS_EMPTY)))
            completeStage()
          case e: ParserValidationError =>
            emitStage(XMLElement(Nil, Map(VALIDATION_INSTRUCTION_FAILURE -> e.toString), Some(VALIDATION_INSTRUCTION_FAILURE)))
            completeStage()
          case e: Throwable =>
            throw e
        }


        private def emitStage(elementsToAdd: XMLElement*) = {
          val streamSize = XMLElement(Nil, Map(STREAM_SIZE -> totalProcessedLength.toString), Some(STREAM_SIZE))
          emit(out, (ByteString(incompleteBytes.toArray), getCompletedXMLElements(xmlElements).toSet ++ elementsToAdd + streamSize))
        }

        /**
          * Move the altoo parser forward and process (Extract/Update/Delete/Insert/etc) the xml elements
          */
        @tailrec
        private def advanceParser(): Unit = {
          if (parser.hasNext) {
            val event = parser.next()
            val offset = totalProcessedLength - parsingData.length
            val (start, end) = getBounds(parser, offset)
            event match {
              case AsyncXMLStreamReader.EVENT_INCOMPLETE => //This will only happen at the end of the xml chunk
                incompleteBytes ++= parsingData.slice(chunkOffset, parsingData.length)

              case XMLStreamConstants.START_ELEMENT =>
                if (continueParsing) numberOfUnclosedXMLTags += 1 //Count tags only as long as we are parsing
                processXMLStartElement(start, end)
                advanceParser()

              case XMLStreamConstants.END_ELEMENT =>
                if (continueParsing) numberOfUnclosedXMLTags -= 1 //Count tags only as long as we are parsing
                processXMLEndElement(start, end)
                advanceParser()

              case XMLStreamConstants.CHARACTERS =>
                processXMLCharacters(start, end)
                advanceParser()

              case XMLStreamConstants.END_DOCUMENT =>
                processXMLEndOfDocument()
                advanceParser()

              case _ =>
                advanceParser()
            }
          }
        }

        /**
          * Handle xml starting tags
          */
        private def processXMLStartElement(start: Int, end: Int): Unit = {
          node += parser.getLocalName
          if (xmlRootOpeningTag.isEmpty) {
            xmlRootOpeningTag = Some(parser.getLocalName)
          }
          instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => e match {
            case instruction@XMLExtract(`node`, _, false) if ExtractNameSpace(parser, instruction.attributes).nonEmpty || instruction.attributes.isEmpty =>
              val keys = ExtractNameSpace(parser, instruction.attributes)
              val ele = XMLElement(instruction.xPath, keys, None)
              xmlElements.add(ele)

            case instruction@XMLExtract(_, _, true) if node.toList == instruction.xPath | elementBlockExtracting =>
              elementBlockExtracting = true
              elementBlock.append(extractBytes(parsingData, start, end).utf8String)

            case instruction: XMLUpdate if instruction.xPath == node.slice(0, instruction.xPath.length) =>
              instruction.xPath match {
                case path if path == node.toList =>
                  val input = getUpdatedElement(instruction.xPath, instruction.attributes, instruction.upsertBlock(parser.getPrefix))(parser).getBytes
                  streamBuffer ++= insertBytes(parsingData, chunkOffset, start, input)
                  chunkOffset = end
                case _ =>
                  chunkOffset = end
              }

            case instruction: XMLValidate if instruction.start == node.slice(0, instruction.start.length) =>
              val newBytes = if (node == instruction.end) ArrayBuffer.empty else parsingData.slice(start, end)
              if (!parser.isEmptyElement) {
                val ele = validators.get(instruction) match {
                  case Some(x) => (instruction, x ++ newBytes)
                  case None => (instruction, ArrayBuffer.empty ++= newBytes)
                }
                validators += ele
                validators.foreach {
                  case (s@XMLValidate(_, `node`, f), testData) =>
                    f(new String(testData.toArray)).map(throw _)
                    completedInstructions += instruction

                  case _ =>
                }
              }

            case instruction: XMLDelete if instruction.xPath == node.slice(0, instruction.xPath.length) =>
              streamBuffer ++= extractBytes(parsingData, chunkOffset, start)
              chunkOffset = end

            case instruction@XMLStopParsing(xpath, _) if node.toList == instruction.xPath =>
              continueParsing = false

            case _ =>
          })

        }

        /**
          * Handle xml starting tags
          */
        private def processXMLEndElement(start: Int, end: Int): Unit = {
          isCharacterBuffering = false
          instructions.diff(completedInstructions).foreach(f = (e: XMLInstruction) => {
            e match {
              case XMLExtract(`node`, _, false) =>
                update(xmlElements, node, Some(bufferedText.toString()))

              case instruction@XMLExtract(_, _, true) if elementBlockExtracting =>
                elementBlock.append(extractBytes(parsingData, start, end).utf8String)
                if (instruction.xPath == node.toList) {
                  val ele = XMLElement(instruction.xPath, Map.empty[String, String], Some(elementBlock.toString()))
                  xmlElements.add(ele)
                  elementBlockExtracting = false
                  elementBlock.clear()
                }
              case instruction@XMLInsertAfter(`node`, elementToInsert) =>
                streamBuffer ++= insertBytes(parsingData, chunkOffset, end, elementToInsert.getBytes)
                completedInstructions += instruction
                chunkOffset = end

              case instruction: XMLUpdate if instruction.xPath.dropRight(1) == node && instruction.isUpsert =>
                val input = getUpdatedElement(instruction.xPath, instruction.attributes, instruction.upsertBlock(parser.getPrefix))(parser).getBytes
                streamBuffer ++= insertBytes(parsingData, chunkOffset, start, input)
                completedInstructions += instruction
                chunkOffset = start

              case instruction: XMLUpdate if instruction.xPath == node.slice(0, instruction.xPath.length) =>
                instruction.xPath match {
                  case path if path == node.toList =>
                    completedInstructions += instruction
                  case _ =>
                }
                chunkOffset = end

              case instruction: XMLValidate if instruction.start == node.slice(0, instruction.start.length) =>
                val newBytes = parsingData.slice(start, end)
                val ele = validators.get(instruction) match {
                  case Some(x) => (instruction, x ++= newBytes)
                  case None => throw new IncompleteXMLValidationException
                }
                validators += ele

              case instruction: XMLDelete if instruction.xPath == node.slice(0, instruction.xPath.length) =>
                chunkOffset = end

              case _ =>
            }
          })
          bufferedText.clear()
          node -= parser.getLocalName
        }

        /**
          * Handle text nodes inside xml chunks
          */
        private def processXMLCharacters(start: Int, end: Int): Unit = {
          instructions.foreach(f = (e: XMLInstruction) => {
            e match {
              case XMLExtract(`node`, _, false) =>
                val t = parser.getText()
                if (t.trim.length > 0) {
                  isCharacterBuffering = true
                  bufferedText.append(t)
                }

              case XMLExtract(_, _, true) if elementBlockExtracting =>
                val t = parser.getText()
                if (t.trim.length > 0) {
                  isCharacterBuffering = true
                  elementBlock.append(t)
                }

              case instruction: XMLUpdate if instruction.xPath == node.slice(0, instruction.xPath.length) =>
                chunkOffset = end

              case instruction: XMLValidate if instruction.start == node.slice(0, instruction.start.length) =>
                val newBytes = parsingData.slice(start, end)
                val ele = validators.get(instruction) match {
                  case Some(x) => (instruction, x ++ newBytes)
                  case None => (instruction, ArrayBuffer.empty ++= newBytes)
                }
                validators += ele

              case instruction: XMLDelete if instruction.xPath == node.slice(0, instruction.xPath.length) =>
                chunkOffset += (end - start)

              case _ =>
            }
          })
        }

        /**
          * Handle xml end of document
          */
        private def processXMLEndOfDocument(): Unit = {
          for {
            i <- instructions.diff(completedInstructions).collect { case v: XMLValidate => v }
          } yield {
            validators.foreach {
              case (s@XMLValidate(_, _, f), testData) =>
                f(new String(testData.toArray)).map(throw _)
                completedInstructions += i
              case _ =>
            }
          }
        }

        /**
          * Calculate relative reader positions from the absolute positions
          * We receive xml files in chunks, as they arrive on the network adapter. We feed the altoo xml parser
          * with these chunks. So the position of parsed elements returned by the altoo parser will represent
          * absolute positions from the beginning of the file (the first chunk). We have to convert these positions
          * so that we can find our location inside the currently processed chunk.
          *
          * @param reader
          * @return
          */
        private def getBounds(reader: AsyncXMLStreamReader[AsyncByteArrayFeeder], offset: Int): (Int, Int) = {
          val start = reader.getLocationInfo.getStartingByteOffset.toInt
          val end = reader.getLocationInfo.getEndingByteOffset.toInt
          val boundStart = if (start == 1) 0 else start - offset
          val boundEnd = end - offset
          (boundStart, boundEnd)
        }


        private def endTagsMismatch() =xmlStopParsing match {
          case Some(stopParsing) if numberOfUnclosedXMLTags == 0 => false
          case Some(stopParsing) if numberOfUnclosedXMLTags == stopParsing.xPath.length => false
          case None if numberOfUnclosedXMLTags == 0 => false
          case _ => true
        }


        /**
          * Extract the closing xml tag from a piece of string. Means turning:
          * </theTag> into theTag  or </ns55:theTag> into theTag
          * @param in
          * @return
          */
        private def extractEndTag(in: String): String = {
          val lastClosingChevron = in.lastIndexOf("""</""")
          val lastNoNamespace = in.indexOf(':', lastClosingChevron)
          val rootStartAt = if (lastNoNamespace >= 0) lastNoNamespace + 1 else lastClosingChevron + 2
          val res = in.substring(rootStartAt, in.indexOf(">", rootStartAt))
          res
        }

        /**
          * Update our buffer storing the last bytes only
          * @param in
          */
        private def updateLastChunk(in: ByteString) = {
          if (in.size > lastChunkBufferSize) { //If we have enough data to fill the whole buffer from the incoming data
            lastChunk = in.takeRight(lastChunkBufferSize)
          } else {  //the incoming data is less than the buffer size, so we have to add it at the end of the existing content
            val temp = lastChunk ++ in
            if (temp.size > lastChunkBufferSize) {  //Add the incoming data chunk, and shorten it to be able to fit  into the buffer
              lastChunk = temp.takeRight(lastChunkBufferSize)
            } else {  //Add the whole incoming data chunk
              lastChunk = temp
            }
          }
        }
      }


  }

}
