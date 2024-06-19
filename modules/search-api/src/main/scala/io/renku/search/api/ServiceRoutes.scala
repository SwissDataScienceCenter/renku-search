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

import io.renku.openid.keycloak.JwtVerify
import io.renku.search.api.auth.Authenticate
import io.renku.search.api.routes.*
import io.renku.search.http.ClientBuilder
import io.renku.search.http.RetryConfig
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Router

/** Defines the routes for the whole search service */
trait ServiceRoutes[F[_]] extends RoutesDefinition[F]:
  def config: SearchApiConfig

object ServiceRoutes:
  def apply[F[_]: Async: Network](cfg: SearchApiConfig): Resource[F, ServiceRoutes[F]] =
    for
      logger <- Resource.pure(scribe.cats.effect[F])
      httpClient <- ClientBuilder(EmberClientBuilder.default[F])
        .withDefaultRetry(RetryConfig.default)
        .withLogging(logBody = false, logger)
        .build

      searchApi <- SearchApi[F](cfg.solrConfig)
      jwtVerify <- Resource.eval(JwtVerify(httpClient, cfg.jwtVerifyConfig))
      authenticate = Authenticate[F](jwtVerify, logger)

      routeDefs = List(
        SearchRoutes(searchApi, authenticate, Nil),
        VersionRoute[F](Nil)
      )
      legacyDefs = List(
        SearchLegacyRoutes(searchApi, authenticate, Nil)
      )
    yield new ServiceRoutes[F] {
      val config = cfg
      val docRoutes = (routeDefs ++ legacyDefs).map(_.docRoutes).reduce(_ ++ _)
      val routes = Router(
        "/api/search" -> routeDefs.map(_.routes).reduce(_ <+> _),
        "/search/query" -> legacyDefs.map(_.routes).reduce(_ <+> _),
        "/search" -> legacyDefs.map(_.routes).reduce(_ <+> _)
      )
    }
