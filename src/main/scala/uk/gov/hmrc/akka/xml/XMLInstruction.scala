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

/**
  * Created by abhishek on 02/12/16.
  */
sealed trait XMLInstruction

case class XMLExtract(xPath: Seq[String], attributes: Map[String, String] = Map.empty, extractBlock: Boolean = false) extends XMLInstruction

case class XMLUpdate(xPath: Seq[String], upsertBlock: String => String, attributes: Map[String, String] = Map.empty, isUpsert: Boolean = false) extends XMLInstruction

case class XMLValidate(start: Seq[String], end: Seq[String], f: String => Option[Throwable]) extends XMLInstruction

case class XMLDelete(xPath: Seq[String]) extends XMLInstruction

case class XMLInsertAfter(xPath: Seq[String], elementToInsert: String) extends XMLInstruction

case class XMLTransform(startPath: Seq[String], endPath: Seq[String], f: String => String) extends XMLInstruction

case class XMLStopParsing(xPath: Seq[String], maxParsingSize: Option[Int] = None) extends XMLInstruction

case class XMLRemoveNamespacePrefix(xPath: Seq[String], removeForStartTag: Boolean = false, removeForEndTag: Boolean = false) extends XMLInstruction

case class XMLRemovePrefixFromStartTag(xPath: Seq[String]) extends XMLInstruction

case class XMLRemovePrefixFromEndTag(xPath: Seq[String]) extends XMLInstruction
