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

package io.renku.search.solr.client

import io.renku.search.solr.SearchRole
import io.renku.search.solr.documents.DocumentKind
import io.renku.search.solr.query.SolrQuery
import io.renku.search.solr.query.SolrToken
import io.renku.search.solr.schema.EntityDocumentSchema
import io.renku.solr.client.*
import io.renku.solr.client.facet.Facet
import io.renku.solr.client.facet.Facets
import io.renku.solr.client.schema.FieldName

/** Convert the user query into a final query that is send to SOLR. */
object RenkuEntityQuery:
  private val typeTerms = Facet.Terms(
    EntityDocumentSchema.Fields.entityType,
    EntityDocumentSchema.Fields.entityType
  )

  private val creatorDetails: FieldName = FieldName("creatorDetails")
  private val namespaceDetails: FieldName = FieldName("namespaceDetails")

  def apply(role: SearchRole, sq: SolrQuery, limit: Int, offset: Int): QueryData =
    QueryData(QueryString(sq.query.value, limit, offset))
      .addFilter(
        SolrToken.kindIs(DocumentKind.FullEntity).value
      )
      .addFilter(constrainRole(role).map(_.value)*)
      .withSort(sq.sort)
      .withFacet(Facets(typeTerms))
      .withFields(FieldName.all, FieldName.score)
      .addSubQuery(
        creatorDetails,
        SubQuery(
          "{!terms f=id v=$row.createdBy}",
          "{!terms f=_kind v=fullentity}",
          1
        ).withFields(FieldName.all)
      )
      .addSubQuery(
        namespaceDetails,
        SubQuery(
          "{!terms f=namespace v=$row.namespace}",
          "(_type:User OR _type:Group) AND _kind:fullentity",
          1
        ).withFields(FieldName.all)
      )

  private def constrainRole(role: SearchRole) = role match
    case SearchRole.Anonymous =>
      Seq(SolrToken.publicOnly)

    case SearchRole.User(id) =>
      Seq(SolrToken.forUser(id))

    case SearchRole.Admin(_) =>
      Seq.empty
