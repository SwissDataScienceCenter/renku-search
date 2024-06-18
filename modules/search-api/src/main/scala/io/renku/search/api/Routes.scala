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

import io.renku.openid.keycloak.{JwtVerify, JwtVerifyConfig}
import io.renku.search.api.auth.Authenticate
import io.renku.search.api.routes.*
import io.renku.search.http.ClientBuilder
import io.renku.search.http.RetryConfig
import io.renku.search.http.metrics.MetricsRoutes
import io.renku.search.http.routes.OperationRoutes
import io.renku.search.metrics.CollectorRegistryBuilder
import io.renku.solr.client.SolrConfig
import org.http4s.HttpRoutes
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Router

object Routes:
  def apply[F[_]: Async: Network](
      solrConfig: SolrConfig,
      jwtVerifyConfig: JwtVerifyConfig
  ): Routes[F] =
    new Routes[F](
      MetricsRoutes[F](CollectorRegistryBuilder[F].withJVMMetrics),
      solrConfig,
      jwtVerifyConfig
    )

final class Routes[F[_]: Async: Network](
    metricsRoutes: MetricsRoutes[F],
    solrConfig: SolrConfig,
    jwtVerifyConfig: JwtVerifyConfig
):
  private val logger = scribe.cats.effect[F]

  private val makeJwtVerify =
    ClientBuilder(EmberClientBuilder.default[F])
      .withDefaultRetry(RetryConfig.default)
      .withLogging(logBody = false, logger)
      .build
      .evalMap(JwtVerify(_, jwtVerifyConfig))

  private val makeSearchRoutes =
    for
      jwtVerify <- makeJwtVerify
      auth = Authenticate[F](jwtVerify, logger)
      api <- SearchApi[F](solrConfig)
    yield SearchRoutes[F](api, auth)

  private def searchHttpRoutes(searchRoutes: SearchRoutes[F]) =
    Router[F](
      "/search" -> (openApiRoute(searchRoutes).routes <+> searchRoutes.routes),
      "/api/search/query" -> (openApiRoute(searchRoutes).routes <+> searchRoutes.routes),
      "/search/query" -> (openApiRoute(searchRoutes).routes <+> searchRoutes.routes)
    )

  private def openApiRoute(searchRoutes: SearchRoutes[F]) =
    OpenApiRoute[F](s"/api/search/query", "Renku Search API", searchRoutes.endpoints)

  private lazy val operationHttpRoutes =
    Router[F]("/" -> OperationRoutes[F])

  lazy val makeRoutes: Resource[F, HttpRoutes[F]] =
    for
      searchRoutes <- makeSearchRoutes
      measuredRoutes <- metricsRoutes.makeRoutes(searchHttpRoutes(searchRoutes))
    yield measuredRoutes <+> operationHttpRoutes
