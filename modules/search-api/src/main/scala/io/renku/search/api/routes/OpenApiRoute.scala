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
import cats.syntax.all.*
import io.circe.syntax.given
import sttp.apispec.openapi.Server
import sttp.apispec.openapi.circe.given
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import io.renku.search.BuildInfo

final class OpenApiRoute[F[_]: Async](
    prefix: String,
    description: String,
    endpoints: List[ServerEndpoint[Any, F]]
) {

  private val openAPIEndpoint =
    val docs = OpenAPIDocsInterpreter()
      .serverEndpointsToOpenAPI(
        endpoints,
        "Search API",
        BuildInfo.gitDescribedVersion.getOrElse(BuildInfo.version)
      )
      .servers(List(Server(url = prefix, description = description.some)))

    endpoint.get
      .in("spec.json")
      .out(stringJsonBody)
      .description("OpenAPI docs")
      .serverLogic(_ => docs.asJson.spaces2.asRight.pure[F])

  def routes =
    Http4sServerInterpreter[F](
      Http4sServerOptions.customiseInterceptors
        .corsInterceptor(CORSInterceptor.default)
        .options
    )
      .toRoutes(openAPIEndpoint)
}
