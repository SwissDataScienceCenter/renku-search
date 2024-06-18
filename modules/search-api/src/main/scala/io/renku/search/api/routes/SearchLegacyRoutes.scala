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

// keep this until gateway is updated to forward to `SearchRoutes`
final class SearchLegacyRoutes[F[_]: Async](
    api: SearchApi[F],
    authenticate: Authenticate[F]
) extends RoutesDefinition[F] {

  private val logger = scribe.cats.effect[F]
  private val baseEndpoint = endpoint.tag("Search (Legacy)").deprecated()

  def queryEndpoint(
      in: Endpoint[Unit, Unit, Unit, Unit, Any]
  ): Endpoint[AuthToken, QueryInput, String, SearchResult, Any] =
    in
      .in(Params.queryInput)
      .securityIn(Params.renkuAuth)
      .errorOut(borerJsonBody[String])
      .out(Params.searchResult)
      .description(SearchQueryManual.markdown)

  val queryPath = queryEndpoint(baseEndpoint.get.in("search"))
  val rootPath = queryEndpoint(baseEndpoint.get.in("search"/"query"))

  override val docRoutes: List[AnyEndpoint] =
    List(queryPath, rootPath)

  override val routes: HttpRoutes[F] =
    RoutesDefinition
      .interpreter[F]
      .toRoutes(
        List(queryPath, rootPath)
          .map(_.serverSecurityLogic(authenticate.apply).serverLogic(api.query))
      )
}
