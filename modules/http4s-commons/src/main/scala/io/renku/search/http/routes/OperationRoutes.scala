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
  enum Paths {
    case Ping
    case Version

    lazy val name: String = productPrefix.toLowerCase()
  }

  def pingEndpoint[F[_]: Async] =
    endpoint.get
      .in(Paths.Ping.name)
      .out(stringBody)
      .description("Ping")
      .serverLogicSuccess[F](_ => "pong".pure[F])

  private given Schema[CurrentVersion] = Schema.derived

  def versionEndpoint[F[_]: Async] =
    endpoint.get
      .in(Paths.Version.name)
      .out(borerJsonBody[CurrentVersion])
      .description("Return version information")
      .serverLogicSuccess[F](_ => CurrentVersion.get.pure[F])

  def apply[F[_]: Async]: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(List(pingEndpoint, versionEndpoint))
}
