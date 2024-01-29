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
import cats.syntax.all.*
import io.renku.solr.client.SolrConfig
import org.http4s.implicits.*
import scribe.Scribe

import scala.concurrent.duration.Duration

object Microservice extends IOApp:

  private given Scribe[IO] = scribe.cats[IO]

  private val solrConfig = SolrConfig(
    baseUrl = uri"http://localhost:8983" / "solr",
    core = "search-core-test",
    commitWithin = Some(Duration.Zero),
    logMessageBodies = true
  )

  override def run(args: List[String]): IO[ExitCode] =
    (createHttpApp >>= HttpServer.build).use(_ => IO.never).as(ExitCode.Success)

  private def createHttpApp = HttpApplication[IO](solrConfig)
