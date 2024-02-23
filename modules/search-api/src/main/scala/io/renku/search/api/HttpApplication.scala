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
import io.renku.solr.client.SolrConfig
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.{HttpApp, HttpRoutes, Response}
import io.renku.search.api.routes.*

object HttpApplication:
  def apply[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, HttpApp[F]] =
    SearchApi[F](solrConfig).map(new HttpApplication[F](_).router)

final class HttpApplication[F[_]: Async](searchApi: SearchApi[F]) extends Http4sDsl[F]:

  private val search = new SearchRoutes[F](searchApi)

  private val businessEndpoints = search.endpoints

  lazy val router: HttpApp[F] =
    Router[F](
      "/search" -> (OpenApiRoute(businessEndpoints) <+> search.routes),
      "/" -> OperationRoutes[F]
    ).orNotFound
