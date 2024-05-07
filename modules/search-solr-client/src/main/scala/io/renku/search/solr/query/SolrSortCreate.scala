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

import cats.data.NonEmptyList

import io.renku.search.query.{Order, SortableField}
import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import io.renku.solr.client.SolrSort
import io.renku.solr.client.schema.FieldName

private object SolrSortCreate:
  private def fromField(field: SortableField): FieldName =
    field match
      case SortableField.Name    => SolrField.name
      case SortableField.Score   => SolrField.score
      case SortableField.Created => SolrField.creationDate

  private def fromDirection(d: Order.Direction): SolrSort.Direction =
    d match
      case Order.Direction.Asc  => SolrSort.Direction.Asc
      case Order.Direction.Desc => SolrSort.Direction.Desc

  def apply(ts: Order.OrderedBy*): SolrSort =
    SolrSort(ts.map(e => (fromField(e.field), fromDirection(e.direction)))*)

  def apply(ts: NonEmptyList[Order.OrderedBy]): SolrSort =
    apply(ts.toList*)
