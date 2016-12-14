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

  def getHeadAndTail(inputBytes: Array[Byte], at: Int, till: Int, insert: Array[Byte], offset: Int) = {
    (inputBytes.slice(0, at) ++ insert.slice(offset, insert.length), inputBytes.slice(till - offset, inputBytes.length))
  }
}