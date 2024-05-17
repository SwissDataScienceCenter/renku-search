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

import java.time.Instant

import cats.Monoid
import cats.data.NonEmptyList
import cats.syntax.all.*

import io.renku.search.model.*
import io.renku.search.model.projects.Visibility
import io.renku.search.query.Comparison
import io.renku.search.solr.documents.DocumentKind
import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import io.renku.solr.client.schema.FieldName

opaque type SolrToken = String

object SolrToken:
  val empty: SolrToken = ""

  def fromString(str: String): SolrToken = StringEscape.queryChars(str)
  def fromId(id: Id): SolrToken = fromString(id.value)
  def fromVisibility(v: Visibility): SolrToken = v.name
  private def fromEntityType(et: EntityType): SolrToken = et.name

  def fromKeyword(kw: Keyword): SolrToken =
    StringEscape.queryChars(kw.value)

  def idIs(id: Id): SolrToken = fieldIs(SolrField.id, fromId(id))

  def entityTypeIs(et: EntityType): SolrToken =
    fieldIs(SolrField.entityType, fromEntityType(et))

  def entityTypeIsAny(ets: NonEmptyList[EntityType]): SolrToken =
    orFieldIs(SolrField.entityType, ets.map(fromEntityType))

  def kindIs(kind: DocumentKind): SolrToken =
    fieldIs(SolrField.kind, kind.name)

  def fromInstant(ts: Instant): SolrToken = StringEscape.escape(ts.toString, ":")
  def fromDateRange(min: Instant, max: Instant): SolrToken = s"[$min TO $max]"

  def fromComparison(op: Comparison): SolrToken =
    op match
      case Comparison.Is          => ":"
      case Comparison.GreaterThan => ">"
      case Comparison.LowerThan   => "<"

  def fromNamespace(ns: Namespace): SolrToken =
    fromString(ns.value)

  def contentAll(text: String): SolrToken =
    val terms: Seq[SolrToken] = text.split("\\s+").map(_.trim).toSeq
    s"${SolrField.contentAll.name}:${terms.fuzzy}"

  def orFieldIs(field: FieldName, values: NonEmptyList[SolrToken]): SolrToken =
    values.map(fieldIs(field, _)).toList.foldOr

  def namespaceIs(ns: Namespace): SolrToken =
    fieldIs(SolrField.namespace, fromNamespace(ns))

  def createdDateIs(date: Instant): SolrToken =
    fieldIs(SolrField.creationDate, fromInstant(date))
  def createdDateGt(date: Instant): SolrToken =
    fieldIs(SolrField.creationDate, s"[${fromInstant(date)} TO *]")
  def createdDateLt(date: Instant): SolrToken =
    fieldIs(SolrField.creationDate, s"[* TO ${fromInstant(date)}]")

  lazy val allEntityTypes: SolrToken =
    List(fieldIs(SolrField.entityType, "*"), kindIs(DocumentKind.FullEntity)).foldAnd

  val publicOnly: SolrToken =
    fieldIs(SolrField.visibility, fromVisibility(Visibility.Public))

  def ownerIs(id: Id): SolrToken = fieldIs(SolrField.owners, fromId(id))
  def editorIs(id: Id): SolrToken = fieldIs(SolrField.editors, fromId(id))
  def viewerIs(id: Id): SolrToken = fieldIs(SolrField.viewers, fromId(id))
  def memberIs(id: Id): SolrToken = fieldIs(SolrField.members, fromId(id))

  def anyMemberIs(id: Id): SolrToken = fieldIs(SolrField.membersAll, fromId(id))

  def groupOwnerIs(id: Id): SolrToken = fieldIs(SolrField.groupOwners, fromId(id))
  def groupEditorIs(id: Id): SolrToken = fieldIs(SolrField.groupEditors, fromId(id))
  def groupViewerIs(id: Id): SolrToken = fieldIs(SolrField.groupViewers, fromId(id))

  def roleIs(id: Id, role: MemberRole): SolrToken = role match
    case MemberRole.Owner  => List(ownerIs(id), groupOwnerIs(id)).foldOr
    case MemberRole.Editor => List(editorIs(id), groupEditorIs(id)).foldOr
    case MemberRole.Viewer => List(viewerIs(id), groupViewerIs(id)).foldOr
    case MemberRole.Member => memberIs(id)

  def roleIn(id: Id, roles: NonEmptyList[MemberRole]): SolrToken =
    roles.toList.distinct.map(roleIs(id, _)).foldOr

  def forUser(id: Id): SolrToken =
    Seq(publicOnly, anyMemberIs(id)).foldOr

  def fieldIs(field: FieldName, value: SolrToken): SolrToken =
    s"${field.name}:$value"

  def fieldExists(field: FieldName): SolrToken =
    fieldIs(field, "*")

  def unsafeFromString(s: String): SolrToken = s

  private def monoidWith(sep: String): Monoid[SolrToken] =
    Monoid.instance(
      empty,
      (a, b) => if (a.isEmpty) b else if (b.isEmpty) a else s"$a$sep$b"
    )
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
    def parens: SolrToken = if (self.isEmpty) self else "(" ~ self ~ ")"

  extension (self: Comparison) def token: SolrToken = fromComparison(self)

  extension (self: Seq[SolrToken])
    def foldM(using Monoid[SolrToken]): SolrToken =
      val all = self.combineAll
      if (self.sizeIs <= 1) all else s"($all)"
    def foldOr: SolrToken = foldM(using orMonoid)
    def foldAnd: SolrToken = foldM(using andMonoid)
    def fuzzy: SolrToken =
      if (self.isEmpty) SolrToken.empty
      else if (self.tail.isEmpty) s"${StringEscape.queryChars(self.head)}~"
      else self.map(StringEscape.queryChars).map(e => s"$e~").mkString("(", " ", ")")
