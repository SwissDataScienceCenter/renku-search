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

package io.renku.search.http.routes

import cats.effect.Async
import cats.syntax.all.*
import org.http4s.HttpRoutes
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import io.renku.search.common.CurrentVersion
import io.renku.search.http.borer.TapirBorerJson

object OperationRoutes extends TapirBorerJson {
  private def pingEndpoint[F[_]: Async] =
    endpoint.get
      .in("ping")
      .out(stringBody)
      .description("Ping")
      .serverLogic[F](_ => "pong".asRight[Unit].pure[F])

  private given Schema[CurrentVersion] = Schema.derived

  private def versionEndpoint[F[_]: Async] =
    endpoint.get
      .in("version")
      .out(borerJsonBody[CurrentVersion])
      .description("Return version information")
      .serverLogicSuccess[F](_ => CurrentVersion.get.pure[F])

  def apply[F[_]: Async]: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(List(pingEndpoint, versionEndpoint))
}
