package io.renku.search.cli

import cats.effect.{ExitCode, IO}

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
    SubCommands.opts.map {
      case SubCommands.PerfTests(opts) =>
        PerfTestsRunner.run(opts).as(ExitCode.Success)

      case SubCommands.Group(opts) =>
        GroupCmd(opts)

      case SubCommands.Project(opts) =>
        ProjectCmd(opts)

      case SubCommands.User(opts) =>
        UserCmd(opts)

      case SubCommands.Reprovision(opts) =>
        ReprovisionCmd(opts)
    }
