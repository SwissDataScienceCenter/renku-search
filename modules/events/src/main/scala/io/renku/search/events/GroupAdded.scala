package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.v2
import io.renku.search.model.*
import org.apache.avro.Schema

sealed trait GroupAdded extends RenkuEventPayload:
  def fold[A](fv2: v2.GroupAdded => A): A
  def withId(id: Id): GroupAdded
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v2.GroupAdded.SCHEMA$)
  val msgType = MsgType.GroupAdded

object GroupAdded:
  def apply(
      id: Id,
      name: Name,
      namespace: Namespace,
      description: Option[Description]
  ): GroupAdded =
    GroupAdded.V2(
      v2.GroupAdded(id.value, name.value, description.map(_.value), namespace.value)
    )

  final case class V2(event: v2.GroupAdded) extends GroupAdded:
    val id: Id = Id(event.id)
    def withId(id: Id): GroupAdded = V2(event.copy(id = id.value))
    def fold[A](fv2: v2.GroupAdded => A): A = fv2(event)

  given AvroEncoder[GroupAdded] =
    val v2e = AvroEncoder[v2.GroupAdded]
    AvroEncoder.basic { v =>
      v.fold(v2e.encode(v.schema))
    }

  given EventMessageDecoder[GroupAdded] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          Left(DecodeFailure.VersionNotSupported(qm.id, qm.header))

        case SchemaVersion.V2 =>
          val schema = v2.GroupAdded.SCHEMA$
          qm.toMessage[v2.GroupAdded](schema)
            .map(_.map(GroupAdded.V2.apply))
    }

  given Show[GroupAdded] = Show.show(_.fold(_.toString))
