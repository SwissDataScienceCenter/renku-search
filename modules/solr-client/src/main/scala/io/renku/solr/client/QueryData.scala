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

package io.renku.solr.client

import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs.deriveEncoder
import io.renku.solr.client.facet.Facets
import io.renku.solr.client.schema.FieldName

final case class QueryData(
    query: String,
    filter: Seq[String],
    limit: Int,
    offset: Int,
    fields: Seq[FieldName] = Seq.empty,
    sort: SolrSort = SolrSort.empty,
    params: Map[String, String] = Map.empty,
    facet: Facets = Facets.empty
):
  def nextPage: QueryData =
    copy(offset = offset + limit)

  def withSort(sort: SolrSort): QueryData = copy(sort = sort)
  def withFields(field: FieldName*) = copy(fields = field)
  def addFilter(q: String): QueryData = copy(filter = filter :+ q)
  def withFacet(facet: Facets): QueryData = copy(facet = facet)
  def withLimit(limit: Int): QueryData = copy(limit = limit)
  def withOffset(offset: Int): QueryData = copy(offset = offset)

object QueryData:

  def apply(query: QueryString): QueryData =
    QueryData(
      query.q,
      Nil,
      query.limit,
      query.offset,
      Nil,
      SolrSort.empty,
      Map.empty,
      Facets.empty
    )

  given Encoder[QueryData] = deriveEncoder
