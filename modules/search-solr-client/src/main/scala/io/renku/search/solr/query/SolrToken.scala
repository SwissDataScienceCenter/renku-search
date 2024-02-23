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
import io.renku.search.solr.documents.{Project as SolrProject, User as SolrUser}
import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import io.renku.solr.client.schema.FieldName
import io.renku.search.query.Comparison
import io.renku.search.model.EntityType
import io.renku.search.model.projects.Visibility

opaque type SolrToken = String

object SolrToken:
  val empty: SolrToken = ""
  def fromString(str: String): SolrToken = Escape.queryChars(str)
  def fromVisibility(v: Visibility): SolrToken = v.name
  def fromEntityType(et: EntityType): SolrToken =
    et match
      case EntityType.Project => SolrProject.entityType
      case EntityType.User    => SolrUser.entityType

  def fromField(field: Field): SolrToken =
    (field match
      case Field.ProjectId  => SolrField.id
      case Field.Name       => SolrField.name
      case Field.Slug       => SolrField.slug
      case Field.Visibility => SolrField.visibility
      case Field.CreatedBy  => SolrField.createdBy
      case Field.Created    => SolrField.creationDate
      case Field.Type       => SolrField.entityType
    ).name

  def fromInstant(ts: Instant): SolrToken = ts.toString
  def fromDateRange(min: Instant, max: Instant): SolrToken = s"[$min TO $max]"

  def fromComparison(op: Comparison): SolrToken =
    op match
      case Comparison.Is          => ":"
      case Comparison.GreaterThan => ">"
      case Comparison.LowerThan   => "<"

  def contentAll(text: String): SolrToken =
    s"${SolrField.contentAll.name}:${Escape.queryChars(text)}"

  def orFieldIs(field: Field, values: NonEmptyList[SolrToken]): SolrToken =
    values.map(fieldIs(field, _)).toList.foldOr

  def dateIs(field: Field, date: Instant): SolrToken = fieldIs(field, fromInstant(date))
  def dateGt(field: Field, date: Instant): SolrToken =
    fieldOp(field, Comparison.GreaterThan, date.toString)
  def dateLt(field: Field, date: Instant): SolrToken =
    fieldOp(field, Comparison.LowerThan, date.toString)

  //TODO: currently only projects work, user can't be decoded
  val allTypes: SolrToken = fieldIs(Field.Type, "Project")

  private def fieldOp(field: Field, op: Comparison, value: SolrToken): SolrToken =
    val cmp = fromComparison(op)
    val f = fromField(field)
    f ~ cmp ~ value

  def fieldIs(field: Field, value: SolrToken): SolrToken =
    fieldOp(field, Comparison.Is, value)

  private def monoidWith(sep: String): Monoid[SolrToken] =
    Monoid.instance(empty, (a, b) => if (a.isEmpty) b else if (b.isEmpty) a else s"$a$sep$b")
  private val orMonoid: Monoid[SolrToken] = monoidWith(" OR ")
  private val andMonoid: Monoid[SolrToken] = monoidWith(" AND ")
  private val spaceMonoid: Monoid[SolrToken] = monoidWith(" ")


  extension (self: SolrToken)
    def value: String = self
    def isEmpty: Boolean = self.isEmpty
    def nonEmpty: Boolean = !self.isEmpty
    def ~(next: SolrToken): SolrToken = self + next
    def +=(next: SolrToken): SolrToken = spaceMonoid.combine(self, next)
    def &&(next: SolrToken): SolrToken = andMonoid.combine(self, next)
    def ||(next: SolrToken): SolrToken = orMonoid.combine(self, next)
    def ===(next: SolrToken): SolrToken = self ~ Comparison.Is.token ~ next
    def <(next: SolrToken): SolrToken = self ~ Comparison.LowerThan.token ~ next
    def >(next: SolrToken): SolrToken = self ~ Comparison.GreaterThan.token ~ next

  extension (self: Comparison)
    def token: SolrToken = fromComparison(self)

  extension (self: Seq[SolrToken])
    def foldM(using Monoid[SolrToken]): SolrToken =
      val all = self.combineAll
      if (self.sizeIs <= 1) all else s"($all)"
    def foldOr: SolrToken = foldM(using orMonoid)
    def foldAnd: SolrToken = foldM(using andMonoid)

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
