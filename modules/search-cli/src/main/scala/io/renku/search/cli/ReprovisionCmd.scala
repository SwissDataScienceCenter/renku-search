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

import cats.effect.*

import com.monovore.decline.Opts
import io.renku.search.cli.reprovision.*

object ReprovisionCmd:

  enum SubCmdOpts:
    case Start(opts: StartCmd.Options)
    case Finish(opts: FinishCmd.Options)

  private val startOpts: Opts[StartCmd.Options] =
    Opts.subcommand("start", "Send reprovisioning-started message")(StartCmd.opts)

  private val finishOpts: Opts[FinishCmd.Options] =
    Opts.subcommand("finish", "Send reprovisioning-finished message")(FinishCmd.opts)

  val opts: Opts[SubCmdOpts] =
    startOpts
      .map(SubCmdOpts.Start.apply)
      .orElse(finishOpts.map(SubCmdOpts.Finish.apply))

  def apply(opts: SubCmdOpts): IO[ExitCode] =
    opts match
      case SubCmdOpts.Start(c)  => StartCmd(c)
      case SubCmdOpts.Finish(c) => FinishCmd(c)
