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

import cats.syntax.all.*
import com.monovore.decline.Opts
import io.renku.search.cli.perftests.PerfTestsConfig

final private case class CliConfig(perfTestConfig: Option[PerfTestsConfig])

private object CliConfig:

  private val perfTestOpts: Opts[PerfTestsConfig] =
    Opts.subcommand("perf-tests", "Run perf tests")(PerfTestsConfig.configOpts)

  val configOpts: Opts[CliConfig] =
    perfTestOpts.orNone.map(CliConfig.apply)
