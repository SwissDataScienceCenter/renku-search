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

package io.renku.search.api

import cats.effect.{Async, Resource}
import fs2.io.net.Network
import io.renku.search.solr.client.SearchSolrClient
import io.renku.solr.client.SolrConfig
import io.renku.search.api.data.*

trait SearchApi[F[_]]:
  def find(phrase: String): F[Either[String, List[SearchEntity]]]
  def query(query: QueryInput): F[Either[String, List[SearchEntity]]]

object SearchApi:
  def apply[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, SearchApi[F]] =
    SearchSolrClient.make[F](solrConfig).map(new SearchApiImpl[F](_))
