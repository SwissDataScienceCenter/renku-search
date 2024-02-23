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

import cats.effect.Sync
import cats.syntax.all.*
import io.renku.search.query.Query

final class QueryInterpreter[F[_]: Sync](ctx: Context[F]) extends QueryTokenEncoders:
  private val encoder = SolrTokenEncoder[F, Query]

  def solrQuery(query: Query): F[String] =
    if (query.isEmpty) SolrToken.allTypes.value.pure[F]
    else encoder.encode(ctx, query).map(t => List(SolrToken.allTypes, t).foldAnd.value)

object QueryInterpreter:
  def apply[F[_]: Sync]: QueryInterpreter[F] =
    new QueryInterpreter[F](Context.forSync[F])
