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
import io.renku.search.BuildInfo
import org.http4s.HttpRoutes
import sttp.apispec.openapi.Server
import sttp.apispec.openapi.circe.given
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

final class OpenApiLegacyRoute[F[_]: Async](
    endpoints: List[AnyEndpoint]
) extends RoutesDefinition[F]:
  private val openAPIEndpoint =
    val docs = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoints,
        "Renku Search API",
        BuildInfo.gitDescribedVersion.getOrElse(BuildInfo.version)
      )
      .servers(
        List(
          Server(
            url = "/api/search",
            description = "Renku Search API".some
          )
        )
      )

    endpoint.get
      .in("search")
      .in("spec.json")
      .out(stringJsonBody)
      .description("OpenAPI docs")
      .serverLogic(_ => docs.asJson.spaces2.asRight.pure[F])

  override val docRoutes = Nil

  override val routes: HttpRoutes[F] =
    RoutesDefinition
      .interpreter[F]
      .toRoutes(openAPIEndpoint)
