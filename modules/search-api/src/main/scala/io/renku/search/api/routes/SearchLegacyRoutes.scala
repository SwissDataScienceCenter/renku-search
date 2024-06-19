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

package io.renku.search.api.routes

import cats.effect.Async

import io.renku.search.api.SearchApi
import io.renku.search.api.auth.Authenticate
import io.renku.search.api.data.*
import io.renku.search.api.tapir.*
import io.renku.search.common.CurrentVersion
import io.renku.search.query.docs.SearchQueryManual
import org.http4s.HttpRoutes
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

// keep this until gateway is updated to forward to `SearchRoutes`
final class SearchLegacyRoutes[F[_]: Async](
    api: SearchApi[F],
    authenticate: Authenticate[F],
    pathPrefix: List[String]
) extends RoutesDefinition[F] {

  private val baseEndpoint = endpoint.in(pathPrefix).tag("Search (Legacy)").deprecated()

  def queryEndpoint(
      in: Endpoint[Unit, Unit, Unit, Unit, Any]
  ): Endpoint[AuthToken, QueryInput, String, SearchResult, Any] =
    in
      .in(Params.queryInput)
      .securityIn(Params.renkuAuth)
      .errorOut(borerJsonBody[String])
      .out(Params.searchResult)
      .description(SearchQueryManual.markdown)

  val queryPath = queryEndpoint(baseEndpoint.get.in("query"))
  val rootPath = queryEndpoint(baseEndpoint.get.in(""))

  val versionEndpoint =
    baseEndpoint.get
      .in("version")
      .out(borerJsonBody[CurrentVersion])
      .description("Returns version information")

  // since our docs are rebased against the `/api/search` we cannot
  // expose this `/query` or `/version` endpoint here again, as it
  // already exists in the standard search routes. Both endpoints will
  // be mounted to different paths, but outside of tapir in order to
  // make up for our url patterns wrt gateway (i.e. have the openapi
  // spec show the server as a path and the endpoints rebased onto
  // that)
  override val docRoutes: List[AnyEndpoint] =
    List(rootPath)

  override val routes: HttpRoutes[F] =
    val endpoints: List[ServerEndpoint[Any, F]] =
      versionEndpoint.serverLogicSuccess[F](_ => Async[F].pure(CurrentVersion.get)) ::
        List(queryPath, rootPath)
          .map(_.serverSecurityLogic(authenticate.apply).serverLogic(api.query))
    RoutesDefinition
      .interpreter[F]
      .toRoutes(endpoints)
}
