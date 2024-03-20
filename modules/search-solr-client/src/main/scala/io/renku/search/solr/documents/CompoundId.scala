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

package io.renku.search.solr.documents

import cats.syntax.all.*

import io.renku.search.model.EntityType
import io.renku.search.model.Id
import io.renku.search.solr.query.SolrToken
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.{QueryData, QueryString}

final case class CompoundId(
    id: Id,
    kind: DocumentKind,
    entityType: Option[EntityType] = None
):
  private[solr] def toQuery: SolrToken =
    List(
      SolrToken.fieldIs(Fields.id, SolrToken.fromId(id)),
      SolrToken.kindIs(kind),
      entityType.map(SolrToken.entityTypeIs).getOrElse(SolrToken.empty)
    ).foldAnd

  private[solr] def toQueryData: QueryData =
    QueryData(QueryString(toQuery.value, 1, 0))

object CompoundId:
  def partial(id: Id, entityType: Option[EntityType] = None): CompoundId =
    CompoundId(id, DocumentKind.Partial, entityType)

  def entity(id: Id, entityType: Option[EntityType] = None): CompoundId =
    CompoundId(id, DocumentKind.Entity, entityType)

  def projectEntity(id: Id): CompoundId = entity(id, EntityType.Project.some)
  def userEntity(id: Id): CompoundId = entity(id, EntityType.User.some)

  def projectPartial(id: Id): CompoundId = partial(id, EntityType.Project.some)
