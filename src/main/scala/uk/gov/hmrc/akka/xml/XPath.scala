package uk.gov.hmrc.akka.xml

/**
  * Created by abhishek on 08/12/16.
  */
object XPath {

  def apply(xpath: String): Seq[String] = xpath.split('/').filterNot(_.isEmpty)

}
