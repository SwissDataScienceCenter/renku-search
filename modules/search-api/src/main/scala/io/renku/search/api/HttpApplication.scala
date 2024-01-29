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

import cats.Monad
import cats.effect.{Async, Resource}
import fs2.io.net.Network
import io.renku.solr.client.SolrConfig
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.{HttpApp, HttpRoutes, Request, Response}
import scribe.Scribe

object HttpApplication:
  def apply[F[_]: Async: Network: Scribe](
      solrConfig: SolrConfig
  ): Resource[F, HttpApp[F]] =
    SearchApi[F](solrConfig).map(new HttpApplication[F](_).router)

class HttpApplication[F[_]: Monad](searchApi: SearchApi[F]) extends Http4sDsl[F]:

  lazy val router: HttpApp[F] =
    Router[F]("/" -> routes).orNotFound

  private lazy val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "api" / phrase => searchApi.find(phrase)
    case GET -> Root / "ping"         => Ok("pong")
  }
