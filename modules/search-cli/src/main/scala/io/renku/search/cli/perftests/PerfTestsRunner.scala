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

package io.renku.search.cli.perftests

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import io.renku.redis.client.ClientId

object PerfTestsRunner:

  def run(config: PerfTestsConfig): IO[Unit] =
    for
      given ModelTypesGenerators[IO] <- ModelTypesGenerators.forIO
      _ <- createProcess(config).use(_.compile.drain)
    yield println("Tests finished")

  private def createProcess(config: PerfTestsConfig)(using ModelTypesGenerators[IO]) =
    for
      creators <- makeDocsCreators(config)
      enqueuer <- makeEnqueuer(config)
    yield creators
      .map(_.newProjectEvents)
      .foldLeft[Stream[IO, NewProjectEvents]](Stream.empty) { case (all, c) => all ++ c }
      .take(config.itemsToGenerate)
      .evalMap(_.toQueueDelivery[IO])
      .flatMap(Stream.emits)
      .through(enqueuer.enqueue)

  private def makeEnqueuer(config: PerfTestsConfig) =
    Enqueuer.make[IO](config.dryRun, ClientId("search-provisioner"))

  private def makeDocsCreators(config: PerfTestsConfig)(using ModelTypesGenerators[IO]) =
    findDocsCreators(config)
      .map(_.map(ProjectEventsGenerator.apply[IO]))
      .sequence

  private def findDocsCreators(config: PerfTestsConfig)(using ModelTypesGenerators[IO]) =
    config.providers.map {
      case Provider.RandommerIO(apiKey) =>
        RandommerIoDocsCreator.make[IO](apiKey)
      case Provider.GitLab(uri) =>
        GitLabDocsCreator.make[IO](uri)
    }
