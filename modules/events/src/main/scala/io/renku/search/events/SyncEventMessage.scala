package io.renku.search.events

import scala.annotation.targetName

type SyncEventMessage = EventMessage[ProjectCreated] | EventMessage[ProjectUpdated] |
  EventMessage[ProjectRemoved] | EventMessage[ProjectMemberAdded] |
  EventMessage[ProjectMemberUpdated] | EventMessage[ProjectMemberRemoved] |
  EventMessage[UserAdded] | EventMessage[UserUpdated] | EventMessage[UserRemoved] |
  EventMessage[GroupAdded] | EventMessage[GroupUpdated] | EventMessage[GroupRemoved] |
  EventMessage[GroupMemberAdded] | EventMessage[GroupMemberUpdated] |
  EventMessage[GroupMemberRemoved] | EventMessage[ReprovisioningStarted] |
  EventMessage[ReprovisioningFinished]

object SyncEventMessage:

  def decode(qm: QueueMessage): Either[DecodeFailure, SyncEventMessage] =
    import syntax.*
    qm.header.msgType match
      case mt: MsgType.ProjectCreated.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.ProjectUpdated.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.ProjectRemoved.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.ProjectMemberAdded.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.ProjectMemberUpdated.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.ProjectMemberRemoved.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.UserAdded.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.UserUpdated.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.UserRemoved.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.GroupAdded.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.GroupUpdated.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.GroupRemoved.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.GroupMemberAdded.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.GroupMemberUpdated.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.GroupMemberRemoved.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.ReprovisioningStarted.type =>
        mt.decoder.decode(qm)
      case mt: MsgType.ReprovisioningFinished.type =>
        mt.decoder.decode(qm)

  // maps each MsgType to its correspondin payload type by providing a
  // cast method paired with the decoder that results in that payload
  // type. so when using this decoder, it is safe to use the
  // corresponding cast
  object syntax {
    extension (self: MsgType.ProjectCreated.type)
      @targetName("castProjectCreated")
      def cast(x: SyncEventMessage): EventMessage[ProjectCreated] =
        x.asInstanceOf[EventMessage[ProjectCreated]]
      @targetName("decoderProjectCreated")
      def decoder: EventMessageDecoder[ProjectCreated] =
        EventMessageDecoder[ProjectCreated]

    extension (self: MsgType.ProjectUpdated.type)
      @targetName("castProjectUpdated")
      def cast(x: SyncEventMessage): EventMessage[ProjectUpdated] =
        x.asInstanceOf[EventMessage[ProjectUpdated]]
      @targetName("decoderProjectUpdated")
      def decoder: EventMessageDecoder[ProjectUpdated] =
        EventMessageDecoder[ProjectUpdated]

    extension (self: MsgType.ProjectRemoved.type)
      @targetName("castProjectRemoved")
      def cast(x: SyncEventMessage): EventMessage[ProjectRemoved] =
        x.asInstanceOf[EventMessage[ProjectRemoved]]
      @targetName("decoderProjectRemoved")
      def decoder: EventMessageDecoder[ProjectRemoved] =
        EventMessageDecoder[ProjectRemoved]

    extension (self: MsgType.ProjectMemberAdded.type)
      @targetName("castProjectMemberAdded")
      def cast(x: SyncEventMessage): EventMessage[ProjectMemberAdded] =
        x.asInstanceOf[EventMessage[ProjectMemberAdded]]
      @targetName("decoderProjectMemberAdded")
      def decoder: EventMessageDecoder[ProjectMemberAdded] =
        EventMessageDecoder[ProjectMemberAdded]

    extension (self: MsgType.ProjectMemberUpdated.type)
      @targetName("castProjectMemberUpdated")
      def cast(x: SyncEventMessage): EventMessage[ProjectMemberUpdated] =
        x.asInstanceOf[EventMessage[ProjectMemberUpdated]]
      @targetName("decoderProjectMemberUpdated")
      def decoder: EventMessageDecoder[ProjectMemberUpdated] =
        EventMessageDecoder[ProjectMemberUpdated]

    extension (self: MsgType.ProjectMemberRemoved.type)
      @targetName("castProjectMemberRemoved")
      def cast(x: SyncEventMessage): EventMessage[ProjectMemberRemoved] =
        x.asInstanceOf[EventMessage[ProjectMemberRemoved]]
      @targetName("decoderProjectMemberRemoved")
      def decoder: EventMessageDecoder[ProjectMemberRemoved] =
        EventMessageDecoder[ProjectMemberRemoved]

    extension (self: MsgType.UserAdded.type)
      @targetName("castUserAdded")
      def cast(x: SyncEventMessage): EventMessage[UserAdded] =
        x.asInstanceOf[EventMessage[UserAdded]]
      @targetName("decoderUserAdded")
      def decoder: EventMessageDecoder[UserAdded] =
        EventMessageDecoder[UserAdded]

    extension (self: MsgType.UserUpdated.type)
      @targetName("castUserUpdated")
      def cast(x: SyncEventMessage): EventMessage[UserUpdated] =
        x.asInstanceOf[EventMessage[UserUpdated]]
      @targetName("decoderUserUpdated")
      def decoder: EventMessageDecoder[UserUpdated] =
        EventMessageDecoder[UserUpdated]

    extension (self: MsgType.UserRemoved.type)
      @targetName("castUserRemoved")
      def cast(x: SyncEventMessage): EventMessage[UserRemoved] =
        x.asInstanceOf[EventMessage[UserRemoved]]
      @targetName("decoderUserRemoved")
      def decoder: EventMessageDecoder[UserRemoved] =
        EventMessageDecoder[UserRemoved]

    extension (self: MsgType.GroupAdded.type)
      @targetName("castGroupAdded")
      def cast(x: SyncEventMessage): EventMessage[GroupAdded] =
        x.asInstanceOf[EventMessage[GroupAdded]]
      @targetName("decoderGroupAdded")
      def decoder: EventMessageDecoder[GroupAdded] =
        EventMessageDecoder[GroupAdded]

    extension (self: MsgType.GroupUpdated.type)
      @targetName("castGroupUpdated")
      def cast(x: SyncEventMessage): EventMessage[GroupUpdated] =
        x.asInstanceOf[EventMessage[GroupUpdated]]
      @targetName("decoderGroupUpdated")
      def decoder: EventMessageDecoder[GroupUpdated] =
        EventMessageDecoder[GroupUpdated]

    extension (self: MsgType.GroupRemoved.type)
      @targetName("castGroupRemoved")
      def cast(x: SyncEventMessage): EventMessage[GroupRemoved] =
        x.asInstanceOf[EventMessage[GroupRemoved]]
      @targetName("decoderGroupRemoved")
      def decoder: EventMessageDecoder[GroupRemoved] =
        EventMessageDecoder[GroupRemoved]

    extension (self: MsgType.GroupMemberAdded.type)
      @targetName("castGroupMemberAdded")
      def cast(x: SyncEventMessage): EventMessage[GroupMemberAdded] =
        x.asInstanceOf[EventMessage[GroupMemberAdded]]
      @targetName("decoderGroupMemberAdded")
      def decoder: EventMessageDecoder[GroupMemberAdded] =
        EventMessageDecoder[GroupMemberAdded]

    extension (self: MsgType.GroupMemberUpdated.type)
      @targetName("castGroupMemberUpdated")
      def cast(x: SyncEventMessage): EventMessage[GroupMemberUpdated] =
        x.asInstanceOf[EventMessage[GroupMemberUpdated]]
      @targetName("decoderGroupMemberUpdated")
      def decoder: EventMessageDecoder[GroupMemberUpdated] =
        EventMessageDecoder[GroupMemberUpdated]

    extension (self: MsgType.GroupMemberRemoved.type)
      @targetName("castGroupMemberRemoved")
      def cast(x: SyncEventMessage): EventMessage[GroupMemberRemoved] =
        x.asInstanceOf[EventMessage[GroupMemberRemoved]]
      @targetName("decoderGroupMemberRemoved")
      def decoder: EventMessageDecoder[GroupMemberRemoved] =
        EventMessageDecoder[GroupMemberRemoved]

    extension (self: MsgType.ReprovisioningStarted.type)
      @targetName("castReprovisioningStarted")
      def cast(x: SyncEventMessage): EventMessage[ReprovisioningStarted] =
        x.asInstanceOf[EventMessage[ReprovisioningStarted]]
      @targetName("decoderReprovisioningStarted")
      def decoder: EventMessageDecoder[ReprovisioningStarted] =
        EventMessageDecoder[ReprovisioningStarted]

    extension (self: MsgType.ReprovisioningFinished.type)
      @targetName("castReprovisioningFinished")
      def cast(x: SyncEventMessage): EventMessage[ReprovisioningFinished] =
        x.asInstanceOf[EventMessage[ReprovisioningFinished]]
      @targetName("decoderReprovisioningFinished")
      def decoder: EventMessageDecoder[ReprovisioningFinished] =
        EventMessageDecoder[ReprovisioningFinished]
  }
