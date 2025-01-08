package io.renku.search.cli

import cats.effect.*

import com.monovore.decline.Opts
import io.renku.search.cli.users.*

object UserCmd:

  enum SubCmdOpts:
    case Add(opts: AddCmd.Options)
    case Update(opts: UpdateCmd.Options)
    case Remove(opts: RemoveCmd.Options)

  private val addOpts: Opts[AddCmd.Options] =
    Opts.subcommand("add", "Add user")(AddCmd.opts)

  private val updateOpts: Opts[UpdateCmd.Options] =
    Opts.subcommand("update", "Update user")(UpdateCmd.opts)

  private val removeOpts: Opts[RemoveCmd.Options] =
    Opts.subcommand("remove", "Remove user")(RemoveCmd.opts)

  val opts: Opts[SubCmdOpts] =
    addOpts
      .map(SubCmdOpts.Add.apply)
      .orElse(updateOpts.map(SubCmdOpts.Update.apply))
      .orElse(removeOpts.map(SubCmdOpts.Remove.apply))

  def apply(opts: SubCmdOpts): IO[ExitCode] =
    opts match
      case SubCmdOpts.Add(c)    => AddCmd(c)
      case SubCmdOpts.Update(c) => UpdateCmd(c)
      case SubCmdOpts.Remove(c) => RemoveCmd(c)
