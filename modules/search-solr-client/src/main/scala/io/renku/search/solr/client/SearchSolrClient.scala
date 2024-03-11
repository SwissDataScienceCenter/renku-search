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

import cats.effect.{Async, Resource}
import fs2.io.net.Network
import io.bullet.borer.Encoder
import io.renku.search.query.Query
import io.renku.search.solr.documents.{DocumentId, Entity}
import io.renku.solr.client.{QueryResponse, SolrClient, SolrConfig}
import cats.data.NonEmptyList

import scala.reflect.ClassTag

trait SearchSolrClient[F[_]]:
  def findById[D <: Entity](id: String)(using ct: ClassTag[D]): F[Option[D]]
  def insert[D: Encoder](documents: Seq[D]): F[Unit]
  def deleteIds(ids: NonEmptyList[DocumentId]): F[Unit]
  def queryEntity(query: Query, limit: Int, offset: Int): F[QueryResponse[Entity]]

object SearchSolrClient:
  def make[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, SearchSolrClient[F]] =
    SolrClient[F](solrConfig).map(new SearchSolrClientImpl[F](_))
