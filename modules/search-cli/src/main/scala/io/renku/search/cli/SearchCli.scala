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

package io.renku.search.cli

import cats.effect.{ExitCode, IO}
import cats.syntax.all.*
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import io.renku.search.cli.perftests.PerfTestsRunner

object SearchCli
    extends CommandIOApp(
      name = "search-cli",
      header = "A set of tools to work with the search services",
      version = "0.0.1"
    ):

  override def main: Opts[IO[ExitCode]] =
    CliConfig.configOpts.map { config =>
      for _ <- config.perfTestConfig.fold(().pure[IO])(PerfTestsRunner.run)
      yield ExitCode.Success
    }
