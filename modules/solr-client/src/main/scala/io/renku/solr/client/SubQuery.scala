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

import cats.syntax.option.*

import io.renku.solr.client.schema.FieldName

final case class SubQuery(
    query: String,
    filter: String,
    limit: Int,
    offset: Int = 0,
    fields: Seq[FieldName] = Seq.empty,
    sort: SolrSort = SolrSort.empty
):
  def withSort(sort: SolrSort): SubQuery = copy(sort = sort)
  def withFields(field: FieldName*) = copy(fields = field)
  def withFilter(q: String): SubQuery = copy(filter = filter)
  def withLimit(limit: Int): SubQuery = copy(limit = limit)
  def withOffset(offset: Int): SubQuery = copy(offset = offset)

  private[client] def toParams(field: FieldName): Map[String, String] =
    def key(s: String): String = s"${field.name}.$s"
    List(
      (key("q") -> query).some,
      Option.when(filter.nonEmpty)(key("fq") -> filter),
      Option.when(limit >= 0)(key("limit") -> limit.toString),
      Option.when(offset > 0)(key("offset") -> offset.toString),
      Option.when(fields.nonEmpty)(key("fl") -> fields.mkString(",")),
      Option.when(sort.nonEmpty)(key("sort") -> sort.toSolr)
    ).collect { case Some(p) => p }.toMap
