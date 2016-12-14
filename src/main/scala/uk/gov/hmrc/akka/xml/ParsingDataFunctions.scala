package uk.gov.hmrc.akka.xml

/**
  * Created by abhishek on 12/12/16.
  */
trait ParsingDataFunctions {
  def getTailBytes(bytes: Array[Byte], end: Int) =
    bytes.slice(end, bytes.length)

  def insertBytes(bytes: Array[Byte], at: Int, insert: Array[Byte]): Array[Byte] = {
    bytes.slice(0, at) ++ insert
  }

  def getHeadAndTail(inputBytes: Array[Byte], start: Int, end: Int, insert: Array[Byte]) = {
    (inputBytes.slice(0, start) ++ insert, inputBytes.slice(end, inputBytes.length))
  }
}