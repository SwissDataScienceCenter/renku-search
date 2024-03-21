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

package io.renku.search.perftests

import cats.effect.std.{Random, UUIDGen}
import cats.effect.{ExitCode, IO}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import fs2.Stream

object EventsGenerator
    extends CommandIOApp(
      name = "perf-tests",
      header = "A set of tools to generate data and provision search services",
      version = "0.0.1"
    ):

  private given UUIDGen[IO] = UUIDGen.fromSync[IO]

  override def main: Opts[IO[ExitCode]] =
    CliConfig.configOpts.map { config =>
      for
        given Random[IO] <- Random.scalaUtilRandom[IO]
        _ <- DocumentsCreator
          .make[IO](config.randommerIoApiKey)
          .map(df => EventsGeneratorImpl[IO](ProjectCreatedGenerator[IO](df)))
          .use(_.generate(config.itemsToGenerate).compile.toList.map(_.map(println)))
      yield ExitCode.Success
    }

private class EventsGeneratorImpl[F[_]](
    projectCreatedGenerator: ProjectCreatedGenerator[F]
):

  def generate(count: Int): Stream[F, NewProjectEvents] =
    projectCreatedGenerator.generateNewProjectEvents
      .take(count)
