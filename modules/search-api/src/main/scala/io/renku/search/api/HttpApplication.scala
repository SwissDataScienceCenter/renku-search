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
import io.renku.search.api.Project.given
import io.renku.search.http.borer.TapirBorerJson
import io.renku.solr.client.SolrConfig
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.{HttpApp, HttpRoutes, Response}
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

object HttpApplication:
  def apply[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, HttpApp[F]] =
    SearchApi[F](solrConfig).map(new HttpApplication[F](_).router)

class HttpApplication[F[_]: Async](searchApi: SearchApi[F])
    extends Http4sDsl[F]
    with TapirBorerJson:

  lazy val router: HttpApp[F] =
    Router[F]("/" -> routes).orNotFound

  private lazy val routes: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(endpoints)

  private lazy val endpoints: List[ServerEndpoint[Any, F]] =
    List(
      searchEndpoint.serverLogic(searchApi.find),
      pingEndpoint.serverLogic[F](_ => "pong".asRight[Unit].pure[F])
    )

  private lazy val searchEndpoint: PublicEndpoint[String, String, List[Project], Any] =
    val query =
      path[String].name("user query").description("User defined query e.g. renku~")
    endpoint.get
      .in("api" / query)
      .errorOut(borerJsonBody[String])
      .out(borerJsonBody[List[Project]])

  private lazy val pingEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get
      .in("ping")
      .out(stringBody)
