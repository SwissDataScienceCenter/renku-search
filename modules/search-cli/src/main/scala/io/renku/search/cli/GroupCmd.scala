package io.renku.search.cli

import cats.effect.*

import com.monovore.decline.Opts
import io.renku.search.cli.groups.*

object GroupCmd:

  enum SubCmdOpts:
    case Add(opts: AddCmd.Options)
    case Update(opts: UpdateCmd.Options)
    case Remove(opts: RemoveCmd.Options)
    case MemberAdd(opts: MemberAddCmd.Options)
    case MemberUpdate(opts: MemberUpdateCmd.Options)
    case MemberRemove(opts: MemberRemoveCmd.Options)

  private val addOpts: Opts[AddCmd.Options] =
    Opts.subcommand("add", "Add group")(AddCmd.opts)

  private val updateOpts: Opts[UpdateCmd.Options] =
    Opts.subcommand("update", "Update group")(UpdateCmd.opts)

  private val removeOpts: Opts[RemoveCmd.Options] =
    Opts.subcommand("remove", "Remove group")(RemoveCmd.opts)

  private val memberAddOpts: Opts[MemberAddCmd.Options] =
    Opts.subcommand("member-add", "Add members to a group")(MemberAddCmd.opts)

  private val memberUpdateOpts: Opts[MemberUpdateCmd.Options] =
    Opts.subcommand("member-update", "Update members of a group")(MemberUpdateCmd.opts)

  private val memberRemoveOpts: Opts[MemberRemoveCmd.Options] =
    Opts.subcommand("member-remove", "Remove members from a group")(MemberRemoveCmd.opts)

  val opts: Opts[SubCmdOpts] =
    addOpts
      .map(SubCmdOpts.Add.apply)
      .orElse(updateOpts.map(SubCmdOpts.Update.apply))
      .orElse(removeOpts.map(SubCmdOpts.Remove.apply))
      .orElse(memberAddOpts.map(SubCmdOpts.MemberAdd.apply))
      .orElse(memberUpdateOpts.map(SubCmdOpts.MemberUpdate.apply))
      .orElse(memberRemoveOpts.map(SubCmdOpts.MemberRemove.apply))

  def apply(opts: SubCmdOpts): IO[ExitCode] =
    opts match
      case SubCmdOpts.Add(c)          => AddCmd(c)
      case SubCmdOpts.Update(c)       => UpdateCmd(c)
      case SubCmdOpts.Remove(c)       => RemoveCmd(c)
      case SubCmdOpts.MemberAdd(c)    => MemberAddCmd(c)
      case SubCmdOpts.MemberUpdate(c) => MemberUpdateCmd(c)
      case SubCmdOpts.MemberRemove(c) => MemberRemoveCmd(c)
