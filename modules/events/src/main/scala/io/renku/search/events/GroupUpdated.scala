package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.v2
import io.renku.search.model.*
import org.apache.avro.Schema

sealed trait GroupUpdated extends RenkuEventPayload:
  def fold[A](fv2: v2.GroupUpdated => A): A
  def withId(id: Id): GroupUpdated
  lazy val version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V2))
  lazy val schema: Schema =
    fold(_ => v2.GroupUpdated.SCHEMA$)
  def namespace: Namespace
  val msgType: MsgType = MsgType.GroupUpdated

object GroupUpdated:
  def apply(
      id: Id,
      name: Name,
      namespace: Namespace,
      description: Option[Description]
  ): GroupUpdated =
    GroupUpdated.V2(
      v2.GroupUpdated(id.value, name.value, description.map(_.value), namespace.value)
    )

  final case class V2(event: v2.GroupUpdated) extends GroupUpdated:
    val id: Id = Id(event.id)
    def withId(id: Id): GroupUpdated = V2(event.copy(id = id.value))
    def fold[A](fv2: v2.GroupUpdated => A): A = fv2(event)
    val namespace: Namespace = Namespace(event.namespace)

  given AvroEncoder[GroupUpdated] =
    val v2e = AvroEncoder[v2.GroupUpdated]
    AvroEncoder.basic { v =>
      v.fold(v2e.encode(v.schema))
    }

  given EventMessageDecoder[GroupUpdated] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          Left(DecodeFailure.VersionNotSupported(qm.id, qm.header))

        case SchemaVersion.V2 =>
          val schema = v2.GroupUpdated.SCHEMA$
          qm.toMessage[v2.GroupUpdated](schema)
            .map(_.map(GroupUpdated.V2.apply))
    }

  given Show[GroupUpdated] = Show.show(_.fold(_.toString))
