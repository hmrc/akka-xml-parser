package uk.gov.hmrc.akka.xml

import com.fasterxml.aalto.{AsyncByteArrayFeeder, AsyncXMLStreamReader}

/*
 * Created by gabor dorcsinecz and joseph griffiths on 07/08/17.
 */
object ExtractNameSpace {

  def apply(parser: AsyncXMLStreamReader[AsyncByteArrayFeeder], targetNameSpace: Map[String, String]): Map[String, String] = {
    val XMLNS = "xmlns"
    val extractedNameSpaces = scala.collection.mutable.Map[String, String]()

    (0 until parser.getNamespaceCount).foreach { i =>
      val ns = if (parser.getNamespacePrefix(i).length == 0) XMLNS else XMLNS + ":" + parser.getNamespacePrefix(i)

      targetNameSpace.foreach{
        case (k,v) =>
          if (v == parser.getNamespaceURI(i)) {
            extractedNameSpaces +=(ns -> parser.getNamespaceURI(i))
          }
      }
    }

    (0 until parser.getAttributeCount).foreach { i =>
      if (targetNameSpace.isEmpty) {
        extractedNameSpaces.+=(parser.getAttributeLocalName(i) -> parser.getAttributeValue(i))
      } else if (targetNameSpace.keySet(parser.getAttributeLocalName(i)) || targetNameSpace.keySet(
        parser.getAttributePrefix(i) + ":" + parser.getAttributeLocalName(i))) {
        extractedNameSpaces.+=(parser.getAttributeLocalName(i) -> parser.getAttributeValue(i))
      }
    }
    extractedNameSpaces
  }.toMap

}
