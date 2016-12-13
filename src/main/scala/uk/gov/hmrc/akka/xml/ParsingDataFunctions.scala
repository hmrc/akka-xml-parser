package uk.gov.hmrc.akka.xml

/**
  * Created by abhishek on 12/12/16.
  */
trait ParsingDataFunctions {
  def getTailBytes(bytes: Array[Byte], end: Int, offset: Int = 0) =
    bytes.slice(end + offset, bytes.length)

  def insertBytes(bytes: Array[Byte], at: Int, insert: Array[Byte], offset: Int = 0): Array[Byte] =
    bytes.slice(0, at + offset) ++ insert ++ bytes.slice(at + offset, bytes.length)


  def getHeadAndTail(inputBytes: Array[Byte], start: Int, end: Int, insert: Array[Byte], offset: Int = 0) = {
    //bytes.slice(0, start + offset) ++ insert ++ bytes.slice(start, bytes.length)
    val s = inputBytes.slice(0, start + offset)
    val t = inputBytes.slice(end + offset, inputBytes.length)
//    println(">>>>>>>>")
//    println(new String(s))
//    println(new String(t))
//    println(offset)
//    println(">>>>>>>>")
    (inputBytes.slice(0, start) ++ insert,inputBytes.slice(end, inputBytes.length))
  }
}