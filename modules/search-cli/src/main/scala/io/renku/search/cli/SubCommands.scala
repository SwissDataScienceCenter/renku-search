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

enum SubCommands:
  case PerfTests(opts: PerfTestsConfig)
  case Group(opts: GroupCmd.SubCmdOpts)
  case Project(opts: ProjectCmd.SubCmdOpts)

private object SubCommands:

  private val perfTestOpts: Opts[PerfTestsConfig] =
    Opts.subcommand("perf-tests", "Run perf tests")(PerfTestsConfig.configOpts)

  private val groupOpts: Opts[GroupCmd.SubCmdOpts] =
    Opts.subcommand("group", "Manage group events")(GroupCmd.opts)

  private val projectOpts: Opts[ProjectCmd.SubCmdOpts] =
    Opts.subcommand("project", "Manage project events")(ProjectCmd.opts)

  val opts: Opts[SubCommands] =
    perfTestOpts
      .map(SubCommands.PerfTests.apply)
      .orElse(groupOpts.map(SubCommands.Group.apply))
      .orElse(projectOpts.map(SubCommands.Project.apply))
