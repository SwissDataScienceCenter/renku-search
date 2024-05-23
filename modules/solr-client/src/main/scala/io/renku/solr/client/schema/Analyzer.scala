/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
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

package io.renku.solr.client.schema

// see https://solr.apache.org/guide/solr/latest/indexing-guide/analyzers.html

final case class Analyzer(
    tokenizer: Tokenizer,
    `type`: Analyzer.AnalyzerType = Analyzer.AnalyzerType.None,
    filters: Seq[Filter] = Nil
)

object Analyzer:
  enum AnalyzerType:
    case Index
    case Multiterm
    case Query
    case None

  object AnalyzerType:
    def fromString(str: String): Either[String, AnalyzerType] =
      AnalyzerType.values
        .find(_.productPrefix.equalsIgnoreCase(str))
        .toRight(s"Invalid analyzer type: $str")

  def index(tokenizer: Tokenizer, filters: Filter*): Analyzer =
    Analyzer(tokenizer, AnalyzerType.Index, filters)

  def query(tokenizer: Tokenizer, filters: Filter*): Analyzer =
    Analyzer(tokenizer, AnalyzerType.Query, filters)

  val classic: Analyzer = Analyzer(Tokenizer.classic, filters = List(Filter.classic))

  val defaultSearch: Analyzer = Analyzer(
    tokenizer = Tokenizer.uax29UrlEmail,
    filters = Seq(
      Filter.lowercase,
      Filter.stop,
      Filter.englishMinimalStem,
      Filter.asciiFolding
    )
  )
