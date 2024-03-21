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
import cats.effect.std.{Random, UUIDGen}
import cats.syntax.all.*

object PerfTestsRunner:

  private given UUIDGen[IO] = UUIDGen.fromSync[IO]

  def run(config: PerfTestsConfig): IO[Unit] =
    for
      given Random[IO] <- Random.scalaUtilRandom[IO]
      r <- findDocsCreators(config)
        .map(
          _.map(ProjectEventsGenerator.apply[IO])
            .use(_.newProjectEvents.take(config.itemsToGenerate).compile.toList)
        )
        .sequence
        .map(_.flatten)
    yield println(r)

  private def findDocsCreators(config: PerfTestsConfig)(using Random[IO]) =
    config.providers.map {
      case Provider.RandommerIO(apiKey) =>
        RandommerIoDocsCreator.make[IO](apiKey)
      case Provider.GitLab(uri) =>
        GitLabDocsCreator.make[IO](uri)
    }
