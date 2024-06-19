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
import io.renku.search.query.docs.SearchQueryManual
import org.http4s.HttpRoutes
import sttp.tapir.*

final class SearchRoutes[F[_]: Async](
    api: SearchApi[F],
    authenticate: Authenticate[F],
    pathPrefix: List[String]
) extends RoutesDefinition[F] {

  private val logger = scribe.cats.effect[F]
  private val baseEndpoint = endpoint.in(pathPrefix).tag("Search")

  val queryEndpoint: Endpoint[AuthToken, QueryInput, String, SearchResult, Any] =
    baseEndpoint.get
      .in("query")
      .in(Params.queryInput)
      .securityIn(Params.renkuAuth)
      .errorOut(borerJsonBody[String])
      .out(Params.searchResult)
      .description(SearchQueryManual.markdown)

  val queryRoute = RoutesDefinition
    .interpreter[F]
    .toRoutes(
      queryEndpoint
        .serverSecurityLogic(authenticate.apply)
        .serverLogic(api.query)
    )

  override val docRoutes: List[AnyEndpoint] =
    List(queryEndpoint)

  override val routes: HttpRoutes[F] =
    queryRoute
}
