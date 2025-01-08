package io.renku.search.cli

import cats.effect.*

import com.monovore.decline.Opts
import io.renku.search.cli.projects.*

object ProjectCmd:

  enum SubCmdOpts:
    case Create(opts: CreateCmd.Options)
    case Update(opts: UpdateCmd.Options)
    case Remove(opts: RemoveCmd.Options)
    case MemberAdd(opts: MemberAddCmd.Options)
    case MemberUpdate(opts: MemberUpdateCmd.Options)
    case MemberRemove(opts: MemberRemoveCmd.Options)

  private val createOpts: Opts[CreateCmd.Options] =
    Opts.subcommand("create", "Create new project")(CreateCmd.opts)

  private val updateOpts: Opts[UpdateCmd.Options] =
    Opts.subcommand("update", "Update a project")(UpdateCmd.opts)

  private val removeOpts: Opts[RemoveCmd.Options] =
    Opts.subcommand("remove", "Remove a project")(RemoveCmd.opts)

  private val memberAddOpts: Opts[MemberAddCmd.Options] =
    Opts.subcommand("member-add", "Add project members")(MemberAddCmd.opts)

  private val memberUpdateOpts: Opts[MemberUpdateCmd.Options] =
    Opts.subcommand("member-update", "Update project members")(MemberUpdateCmd.opts)

  private val memberRemoveOpts: Opts[MemberRemoveCmd.Options] =
    Opts.subcommand("member-remove", "Remove project members")(MemberRemoveCmd.opts)

  val opts: Opts[SubCmdOpts] =
    createOpts
      .map(SubCmdOpts.Create.apply)
      .orElse(updateOpts.map(SubCmdOpts.Update.apply))
      .orElse(removeOpts.map(SubCmdOpts.Remove.apply))
      .orElse(memberAddOpts.map(SubCmdOpts.MemberAdd.apply))
      .orElse(memberUpdateOpts.map(SubCmdOpts.MemberUpdate.apply))
      .orElse(memberRemoveOpts.map(SubCmdOpts.MemberRemove.apply))

  def apply(opts: SubCmdOpts): IO[ExitCode] =
    opts match
      case SubCmdOpts.Create(c)       => CreateCmd(c)
      case SubCmdOpts.Update(c)       => UpdateCmd(c)
      case SubCmdOpts.Remove(c)       => RemoveCmd(c)
      case SubCmdOpts.MemberAdd(c)    => MemberAddCmd(c)
      case SubCmdOpts.MemberUpdate(c) => MemberUpdateCmd(c)
      case SubCmdOpts.MemberRemove(c) => MemberRemoveCmd(c)
