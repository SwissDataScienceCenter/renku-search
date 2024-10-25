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

import cats.Monad
import cats.effect.Sync
import cats.syntax.all.*

import io.renku.search.query.Query
import io.renku.search.solr.SearchRole

/** Provides conversion into solrs standard query. See
  * https://solr.apache.org/guide/solr/latest/query-guide/standard-query-parser.html
  */
final class LuceneQueryInterpreter[F[_]: Monad]
    extends QueryInterpreter[F]
    with LuceneQueryEncoders:
  private val encoder = SolrTokenEncoder[F, Query]

  def run(ctx: Context[F], query: Query): F[SolrQuery] =
    encoder.encode(ctx, query).map(_.emptyToAll)

object LuceneQueryInterpreter:
  def forSync[F[_]: Sync](role: SearchRole): QueryInterpreter.WithContext[F] =
    QueryInterpreter.withContext(LuceneQueryInterpreter[F], Context.forSync[F](role))
