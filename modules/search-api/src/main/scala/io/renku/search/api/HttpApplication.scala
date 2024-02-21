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
import io.renku.search.http.borer.TapirBorerJson
import io.renku.solr.client.SolrConfig
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.{HttpApp, HttpRoutes, Response}
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

object HttpApplication:
  def apply[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, HttpApp[F]] =
    SearchApi[F](solrConfig).map(new HttpApplication[F](_).router)

class HttpApplication[F[_]: Async](searchApi: SearchApi[F])
    extends Http4sDsl[F]
    with TapirBorerJson:

  private val businessRoot = "search"

  lazy val router: HttpApp[F] =
    Router[F](
      s"/$businessRoot" -> businessRoutes,
      "/" -> operationsRoutes
    ).orNotFound

  private lazy val businessRoutes: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(swaggerEndpoints ::: businessEndpoints)

  private lazy val businessEndpoints: List[ServerEndpoint[Any, F]] =
    List(
      searchEndpoint.serverLogic(searchApi.find)
    )

  private lazy val searchEndpoint: PublicEndpoint[String, String, List[SearchEntity], Any] =
    val query =
      path[String].name("user query").description("User defined query e.g. renku~")
    endpoint.get
      .in(query)
      .errorOut(borerJsonBody[String])
      .out(borerJsonBody[List[SearchEntity]])
      .description("Search API for searching Renku entities")

  private lazy val swaggerEndpoints =
    SwaggerInterpreter(
      swaggerUIOptions = SwaggerUIOptions.default.copy(contextPath = List(businessRoot))
    ).fromServerEndpoints[F](businessEndpoints, "Search API", "0.0.1")

  private lazy val operationsRoutes: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(List(pingEndpoint))

  private lazy val pingEndpoint =
    endpoint.get
      .in("ping")
      .out(stringBody)
      .description("Ping")
      .serverLogic[F](_ => "pong".asRight[Unit].pure[F])
