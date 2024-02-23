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

package io.renku.search.solr.query

import cats.syntax.all.*
import io.renku.search.query.Field
import cats.data.NonEmptyList
import cats.Monoid
import java.time.Instant
import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import io.renku.solr.client.schema.FieldName

opaque type SolrToken = String

object SolrToken:
  def apply(str: String): SolrToken = str
  def escaped(str: String): SolrToken = Escape.queryChars(str)

  def contentAll(text: String): SolrToken =
    s"${SolrField.contentAll.name}:${Escape.queryChars(text)}"

  def fieldIs(field: Field, value: SolrToken): SolrToken =
    val name = solrField(field).name
    s"${name}:${value.value}"

  def orFieldIs(field: Field, values: NonEmptyList[String]): SolrToken =
    values.map(Escape.queryChars).map(fieldIs(field, _)).toList.foldOr

  def dateRange(field: Field, min: Instant, max: Instant): SolrToken =
    s"${solrField(field).name}:[$min TO $max]"

  def dateIs(field: Field, date: Instant): SolrToken = fieldIs(field, date.toString)
  def dateGt(field: Field, date: Instant): SolrToken = ???
  def dateLt(field: Field, date: Instant): SolrToken = ???

  val allTypes: SolrToken = fieldIs(Field.Type, "*")

  private def solrField(field: Field): FieldName =
    field match
      case Field.ProjectId => SolrField.id
      case Field.Name => SolrField.name
      case Field.Slug => SolrField.slug
      case Field.Visibility => SolrField.visibility
      case Field.CreatedBy => SolrField.createdBy
      case Field.Created => SolrField.creationDate
      case Field.Type => SolrField.entityType

  private val orMonoid: Monoid[SolrToken] =
    Monoid.instance("", (a, b) => if (a.isEmpty) b else if (b.isEmpty) a else s"$a OR $b")
  private val andMonoid: Monoid[SolrToken] = Monoid.instance(
    "",
    (a, b) => if (a.isEmpty) b else if (b.isEmpty) a else s"$a AND $b"
  )

  extension (self: SolrToken)
    def value: String = self

  extension (self: Seq[SolrToken])
    def foldOr: SolrToken =
      given Monoid[SolrToken] = orMonoid
      if (self.isEmpty) ""
      else if (self.tail.isEmpty) self.head
      else s"( ${self.combineAll} )"

    def foldAnd: SolrToken =
      given Monoid[SolrToken] = andMonoid
      if (self.isEmpty) ""
      else if (self.tail.isEmpty) self.head
      else s"( ${self.combineAll} )"

  // Escapes query characters for solr. This is taken from here:
  // https://github.com/apache/solr/blob/bcb9f144974ed07aa3b66766302474542067b522/solr/solrj/src/java/org/apache/solr/client/solrj/util/ClientUtils.java#L163
  // to not introduce too many dependencies only for this little function
  private object Escape {
    private[this] val specialChars = "\\+-!():^[]\"{}~*?|&;/"

    private inline def isSpecial(c: Char) = c.isWhitespace || specialChars.contains(c)

    def queryChars(s: String): String = {
      val sb = new StringBuilder();
      s.foreach { c =>
        if (isSpecial(c)) sb.append('\\')
        sb.append(c)
      }
      s.toString
    }
  }
