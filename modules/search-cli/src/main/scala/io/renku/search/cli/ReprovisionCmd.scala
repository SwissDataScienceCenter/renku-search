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
