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

import cats.data.NonEmptyList
import cats.effect.{Async, Resource}
import fs2.Stream
import fs2.io.net.Network
import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.model.Id
import io.renku.search.query.Query
import io.renku.search.solr.SearchRole
import io.renku.search.solr.documents.*
import io.renku.solr.client.*

trait SearchSolrClient[F[_]]:
  def findById[D: Decoder](id: CompoundId): F[Option[D]]
  def upsert[D: Encoder](documents: Seq[D]): F[Unit]
  def deleteIds(ids: NonEmptyList[Id]): F[Unit]
  def query[D: Decoder](query: QueryData): F[QueryResponse[D]]
  def queryEntity(
      role: SearchRole,
      query: Query,
      limit: Int,
      offset: Int
  ): F[QueryResponse[EntityDocument]]
  def queryAll[D: Decoder](query: QueryData): Stream[F, D]

object SearchSolrClient:
  def make[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, SearchSolrClient[F]] =
    SolrClient[F](solrConfig).map(new SearchSolrClientImpl[F](_))
