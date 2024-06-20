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

import cats.effect.*
import cats.syntax.all.*

import io.renku.search.common.CurrentVersion
import org.http4s.HttpRoutes
import sttp.tapir.*

final class VersionRoute[F[_]: Async](pathPrefix: List[String])
    extends RoutesDefinition[F]:
  private val baseEndpoint = endpoint.in(pathPrefix).tag("Information")

  val versionEndpoint =
    baseEndpoint.get
      .in("version")
      .out(borerJsonBody[CurrentVersion])
      .description("Returns version information")

  val versionRoute = RoutesDefinition
    .interpreter[F]
    .toRoutes(
      versionEndpoint.serverLogicSuccess[F](_ => CurrentVersion.get.pure[F])
    )

  override val docEndpoints: List[AnyEndpoint] =
    List(versionEndpoint)

  override val routes: HttpRoutes[F] =
    versionRoute
