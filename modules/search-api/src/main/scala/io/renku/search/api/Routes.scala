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
import cats.syntax.all.*
import fs2.io.net.Network
import io.renku.search.api.routes.*
import io.renku.search.http.metrics.MetricsRoutes
import io.renku.search.http.routes.OperationRoutes
import io.renku.search.metrics.CollectorRegistryBuilder
import io.renku.solr.client.SolrConfig
import org.http4s.HttpRoutes
import org.http4s.server.Router

private object Routes:

  def apply[F[_]: Async: Network](solrConfig: SolrConfig): Resource[F, HttpRoutes[F]] =
    for
      search <- SearchApi[F](solrConfig).map(api => SearchRoutes[F](api))
      metricsRoutes <- MetricsRoutes[F](
        CollectorRegistryBuilder[F].withStandardJVMMetrics
      ).makeRoutes(search.routes)
    yield new Routes[F](search, metricsRoutes).routes

final private class Routes[F[_]: Async](
    searchRoutes: SearchRoutes[F],
    metricsRoutes: HttpRoutes[F]
):

  private val prefix = "/search"

  private val openapi =
    OpenApiRoute[F](s"/api$prefix", "Renku Search API", searchRoutes.endpoints)

  private lazy val searchAndOperationRoutes =
    Router[F](
      prefix -> (openapi.routes <+> searchRoutes.routes),
      "/" -> OperationRoutes[F]
    )

  lazy val routes: HttpRoutes[F] =
    searchAndOperationRoutes <+> metricsRoutes
