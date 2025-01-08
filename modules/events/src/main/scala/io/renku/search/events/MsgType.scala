package io.renku.search.events

/** This represents expected values of the `type` property in the message header. */
enum MsgType(val name: String):
  case ProjectCreated extends MsgType("project.created")
  case ProjectUpdated extends MsgType("project.updated")
  case ProjectRemoved extends MsgType("project.removed")
  case ProjectMemberAdded extends MsgType("projectAuth.added")
  case ProjectMemberUpdated extends MsgType("projectAuth.updated")
  case ProjectMemberRemoved extends MsgType("projectAuth.removed")
  case UserAdded extends MsgType("user.added")
  case UserUpdated extends MsgType("user.updated")
  case UserRemoved extends MsgType("user.removed")
  case GroupAdded extends MsgType("group.added")
  case GroupUpdated extends MsgType("group.updated")
  case GroupRemoved extends MsgType("group.removed")
  case GroupMemberAdded extends MsgType("memberGroup.added")
  case GroupMemberUpdated extends MsgType("memberGroup.updated")
  case GroupMemberRemoved extends MsgType("memberGroup.removed")
  case ReprovisioningStarted extends MsgType("reprovisioning.started")
  case ReprovisioningFinished extends MsgType("reprovisioning.finished")

object MsgType:
  def fromString(s: String): Either[String, MsgType] =
    MsgType.values
      .find(e => e.name.equalsIgnoreCase(s) || e.productPrefix.equalsIgnoreCase(s))
      .toRight(s"Invalid msg type: $s")
