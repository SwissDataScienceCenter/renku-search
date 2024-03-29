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

import cats.effect.{ExitCode, IO, IOApp}
import io.renku.logging.LoggingSetup
import io.renku.search.http.HttpServer

object Microservice extends IOApp:

  private val loadConfig = SearchApiConfig.config.load[IO]

  override def run(args: List[String]): IO[ExitCode] =
    for {
      config <- loadConfig
      _ <- IO(LoggingSetup.doConfigure(config.verbosity))
      _ <- Routes[IO](config.solrConfig).makeRoutes
        .flatMap(HttpServer.build(_, config.httpServerConfig))
        .use(_ => IO.never)
    } yield ExitCode.Success
