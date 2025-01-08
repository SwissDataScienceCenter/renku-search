package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.v2
import io.renku.search.model.Id
import org.apache.avro.Schema

sealed trait GroupMemberRemoved extends RenkuEventPayload:
  def fold[A](fv2: v2.GroupMemberRemoved => A): A
  def withId(id: Id): GroupMemberRemoved
  def version: NonEmptyList[SchemaVersion] = NonEmptyList.of(SchemaVersion.V2)
  def schema: Schema = v2.GroupMemberRemoved.SCHEMA$
  def userId: Id = fold(a => Id(a.userId))
  val msgType: MsgType = MsgType.GroupMemberRemoved

object GroupMemberRemoved:
  def apply(groupId: Id, userId: Id): GroupMemberRemoved =
    V2(v2.GroupMemberRemoved(groupId.value, userId.value))

  final case class V2(event: v2.GroupMemberRemoved) extends GroupMemberRemoved:
    lazy val id: Id = Id(event.groupId)
    def withId(id: Id): GroupMemberRemoved = V2(event.copy(groupId = id.value))
    def fold[A](fv2: v2.GroupMemberRemoved => A): A = fv2(event)

  given AvroEncoder[GroupMemberRemoved] =
    val v2e = AvroEncoder[v2.GroupMemberRemoved]
    AvroEncoder.basic { v =>
      v.fold(v2e.encode(v.schema))
    }

  given EventMessageDecoder[GroupMemberRemoved] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          Left(DecodeFailure.VersionNotSupported(qm.id, qm.header))

        case SchemaVersion.V2 =>
          val schema = v2.GroupMemberRemoved.SCHEMA$
          qm.toMessage[v2.GroupMemberRemoved](schema)
            .map(_.map(GroupMemberRemoved.V2.apply))
    }

  given Show[GroupMemberRemoved] =
    Show.show(_.fold(_.toString))
