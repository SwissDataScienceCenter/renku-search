package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.{v1, v2}
import io.renku.search.model.{Id, MemberRole}
import org.apache.avro.Schema

sealed trait ProjectMemberAdded extends RenkuEventPayload:
  def fold[A](fv1: v1.ProjectAuthorizationAdded => A, fv2: v2.ProjectMemberAdded => A): A
  def withId(id: Id): ProjectMemberAdded
  def withRole(role: MemberRole): ProjectMemberAdded
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v1.ProjectAuthorizationAdded.SCHEMA$, _ => v2.ProjectMemberAdded.SCHEMA$)
  def userId: Id = fold(a => Id(a.userId), b => Id(b.userId))
  def role: MemberRole
  val msgType: MsgType = MsgType.ProjectMemberAdded

object ProjectMemberAdded:
  def apply(projectId: Id, userId: Id, role: MemberRole): ProjectMemberAdded =
    V2(v2.ProjectMemberAdded(projectId.value, userId.value, v2.MemberRole.VIEWER))
      .withRole(role)

  final case class V1(event: v1.ProjectAuthorizationAdded) extends ProjectMemberAdded:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberAdded = V1(event.copy(projectId = id.value))
    def withRole(role: MemberRole): ProjectMemberAdded =
      role match
        case MemberRole.Member => V1(event.copy(role = v1.ProjectMemberRole.MEMBER))
        case MemberRole.Viewer => V1(event.copy(role = v1.ProjectMemberRole.MEMBER))
        case MemberRole.Editor => V1(event.copy(role = v1.ProjectMemberRole.MEMBER))
        case MemberRole.Owner  => V1(event.copy(role = v1.ProjectMemberRole.OWNER))

    def fold[A](
        fv1: v1.ProjectAuthorizationAdded => A,
        fv2: v2.ProjectMemberAdded => A
    ): A = fv1(event)
    val role: MemberRole = event.role match
      case v1.ProjectMemberRole.OWNER  => MemberRole.Owner
      case v1.ProjectMemberRole.MEMBER => MemberRole.Member

  final case class V2(event: v2.ProjectMemberAdded) extends ProjectMemberAdded:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberAdded = V2(event.copy(projectId = id.value))
    def withRole(role: MemberRole): ProjectMemberAdded =
      role match
        case MemberRole.Member => V2(event.copy(role = v2.MemberRole.VIEWER))
        case MemberRole.Viewer => V2(event.copy(role = v2.MemberRole.VIEWER))
        case MemberRole.Editor => V2(event.copy(role = v2.MemberRole.EDITOR))
        case MemberRole.Owner  => V2(event.copy(role = v2.MemberRole.OWNER))

    def fold[A](
        fv1: v1.ProjectAuthorizationAdded => A,
        fv2: v2.ProjectMemberAdded => A
    ): A = fv2(event)

    val role: MemberRole = event.role match
      case v2.MemberRole.OWNER  => MemberRole.Owner
      case v2.MemberRole.EDITOR => MemberRole.Editor
      case v2.MemberRole.VIEWER => MemberRole.Viewer

  given AvroEncoder[ProjectMemberAdded] =
    val v1e = AvroEncoder[v1.ProjectAuthorizationAdded]
    val v2e = AvroEncoder[v2.ProjectMemberAdded]
    AvroEncoder.basic { v =>
      v.fold(v1e.encode(v.schema), v2e.encode(v.schema))
    }

  given EventMessageDecoder[ProjectMemberAdded] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.ProjectAuthorizationAdded.SCHEMA$
          qm.toMessage[v1.ProjectAuthorizationAdded](schema)
            .map(_.map(ProjectMemberAdded.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.ProjectMemberAdded.SCHEMA$
          qm.toMessage[v2.ProjectMemberAdded](schema)
            .map(_.map(ProjectMemberAdded.V2.apply))
    }

  given Show[ProjectMemberAdded] =
    Show.show(_.fold(_.toString, _.toString))
