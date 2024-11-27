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

import cats.Monoid
import cats.syntax.all.*

import io.renku.search.query.Order
import io.renku.solr.client.SolrSort

final case class SolrQuery(
    query: SolrToken,
    sort: SolrSort
):
  def withQuery(q: SolrToken): SolrQuery = copy(query = q)
  def ++(next: SolrQuery): SolrQuery =
    SolrQuery(query && next.query, sort ++ next.sort)

  def emptyToAll: SolrQuery =
    if (query.isEmpty) SolrQuery(SolrToken.all, sort)
    else this

object SolrQuery:
  val empty: SolrQuery = SolrQuery(SolrToken.empty, SolrSort.empty)

  def apply(e: SolrToken): SolrQuery =
    SolrQuery(e, SolrSort.empty)

  def sort(order: Order): SolrQuery =
    SolrQuery(SolrToken.empty, SolrSortCreate(order.fields))

  given Monoid[SolrQuery] = Monoid.instance(empty, (a, b) => a ++ b)
