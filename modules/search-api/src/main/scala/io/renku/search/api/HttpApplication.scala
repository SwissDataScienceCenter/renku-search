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
import io.circe.syntax.given
import io.renku.search.http.borer.TapirBorerJson
import io.renku.solr.client.SolrConfig
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.{HttpApp, HttpRoutes, Response}
import sttp.apispec.openapi.Server
import sttp.apispec.openapi.circe.given
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import io.renku.search.query.Query
import io.renku.search.api.data.*
import io.renku.search.query.docs.SearchQueryManual
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.cors.CORSInterceptor

object HttpApplication:
  def apply[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, HttpApp[F]] =
    SearchApi[F](solrConfig).map(new HttpApplication[F](_).router)

class HttpApplication[F[_]: Async](searchApi: SearchApi[F])
    extends Http4sDsl[F]
    with TapirBorerJson
    with TapirCodecs:

  private val businessRoot = "search"

  lazy val router: HttpApp[F] =
    Router[F](
      s"/$businessRoot" -> (openAPIRoute <+> businessRoutes),
      "/" -> operationsRoutes
    ).orNotFound

  private lazy val businessRoutes: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(businessEndpoints)

  private lazy val businessEndpoints: List[ServerEndpoint[Any, F]] =
    List(
      searchEndpointGet.serverLogic(searchApi.query),
      searchEndpointPost.serverLogic(searchApi.query)
    )

  private lazy val searchEndpointGet
      : PublicEndpoint[QueryInput, String, List[SearchEntity], Any] =
    endpoint.get
      .in(Params.queryInput)
      .errorOut(borerJsonBody[String])
      .out(borerJsonBody[List[SearchEntity]])
      .description(SearchQueryManual.markdown)

  private val searchEndpointPost
      : PublicEndpoint[QueryInput, String, List[SearchEntity], Any] =
    endpoint.post
      .errorOut(borerJsonBody[String])
      .in(
        borerJsonBody[QueryInput]
          .example(
            QueryInput(
              Query(Query.Segment.nameIs("proj-name1"), Query.Segment.text("flight sim")),
              PageDef.default
            )
          )
      )
      .out(borerJsonBody[List[SearchEntity]])
      .description(SearchQueryManual.markdown)

  private lazy val openAPIEndpoint =
    val docs = OpenAPIDocsInterpreter()
      .serverEndpointsToOpenAPI(businessEndpoints, "Search API", "0.0.1")
      .servers(List(Server(url = "/search", description = "Renku Search API".some)))

    endpoint.get
      .in("spec.json")
      .out(stringJsonBody)
      .description("OpenAPI docs")
      .serverLogic(_ => docs.asJson.spaces2.asRight.pure[F])

  private val openAPIRoute =
    Http4sServerInterpreter[F](
      Http4sServerOptions.customiseInterceptors
        .corsInterceptor(CORSInterceptor.default)
        .options
    )
      .toRoutes(List(openAPIEndpoint))

  private lazy val operationsRoutes: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(List(pingEndpoint))

  private lazy val pingEndpoint =
    endpoint.get
      .in("ping")
      .out(stringBody)
      .description("Ping")
      .serverLogic[F](_ => "pong".asRight[Unit].pure[F])
