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

import cats.effect.*

import io.renku.logging.LoggingSetup

object Microservice extends IOApp:
  private val logger = scribe.cats.io
  private val loadConfig = SearchApiConfig.config.load[IO]
  val pathPrefix = List("api", "search")

  override def run(args: List[String]): IO[ExitCode] =
    createServer.useForever

  def createServer =
    for {
      config <- Resource.eval(loadConfig)
      _ <- Resource.eval(IO(LoggingSetup.doConfigure(config.verbosity)))
      logger <- Resource.pure(scribe.cats.io)
      app <- ServiceRoutes[IO](config, pathPrefix)
      server <- SearchServer.create[IO](config, app)
      _ <- Resource.eval(
        logger.info(
          s"Search microservice running: ${config.httpServerConfig}"
        )
      )
    } yield server
