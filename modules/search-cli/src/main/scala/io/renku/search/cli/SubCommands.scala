package io.renku.search.cli

import cats.syntax.all.*

import com.monovore.decline.Opts
import io.renku.search.cli.perftests.PerfTestsConfig

enum SubCommands:
  case PerfTests(opts: PerfTestsConfig)
  case Group(opts: GroupCmd.SubCmdOpts)
  case Project(opts: ProjectCmd.SubCmdOpts)
  case User(opts: UserCmd.SubCmdOpts)
  case Reprovision(opts: ReprovisionCmd.SubCmdOpts)

private object SubCommands:

  private val perfTestOpts: Opts[PerfTestsConfig] =
    Opts.subcommand("perf-tests", "Run perf tests")(PerfTestsConfig.configOpts)

  private val groupOpts: Opts[GroupCmd.SubCmdOpts] =
    Opts.subcommand("group", "Manage group events")(GroupCmd.opts)

  private val projectOpts: Opts[ProjectCmd.SubCmdOpts] =
    Opts.subcommand("project", "Manage project events")(ProjectCmd.opts)

  private val userOpts: Opts[UserCmd.SubCmdOpts] =
    Opts.subcommand("user", "Manage user events")(UserCmd.opts)

  private val reprovisionOpts: Opts[ReprovisionCmd.SubCmdOpts] =
    Opts.subcommand("reprovision", "Send reprovisioning messages")(ReprovisionCmd.opts)

  val opts: Opts[SubCommands] =
    perfTestOpts
      .map(SubCommands.PerfTests.apply)
      .orElse(groupOpts.map(SubCommands.Group.apply))
      .orElse(projectOpts.map(SubCommands.Project.apply))
      .orElse(userOpts.map(SubCommands.User.apply))
      .orElse(reprovisionOpts.map(SubCommands.Reprovision.apply))
