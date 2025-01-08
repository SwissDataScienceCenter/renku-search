package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.{v1, v2}
import io.renku.search.model.*
import org.apache.avro.Schema

sealed trait UserAdded extends RenkuEventPayload:
  def fold[A](fv1: v1.UserAdded => A, fv2: v2.UserAdded => A): A
  def withId(id: Id): UserAdded
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v1.UserAdded.SCHEMA$, _ => v2.UserAdded.SCHEMA$)
  val msgType: MsgType = MsgType.UserAdded

object UserAdded:
  def apply(
      id: Id,
      namespace: Namespace,
      firstName: Option[FirstName],
      lastName: Option[LastName],
      email: Option[Email]
  ): UserAdded =
    V2(
      v2.UserAdded(
        id.value,
        firstName.map(_.value),
        lastName.map(_.value),
        email.map(_.value),
        namespace.value
      )
    )

  final case class V1(event: v1.UserAdded) extends UserAdded:
    val id: Id = Id(event.id)
    def withId(id: Id): UserAdded = V1(event.copy(id = id.value))
    def fold[A](fv1: v1.UserAdded => A, fv2: v2.UserAdded => A): A = fv1(event)
    val namespace: Option[Namespace] = None

  final case class V2(event: v2.UserAdded) extends UserAdded:
    val id: Id = Id(event.id)
    def withId(id: Id): UserAdded = V2(event.copy(id = id.value))
    def fold[A](fv1: v1.UserAdded => A, fv2: v2.UserAdded => A): A = fv2(event)
    val namespace: Option[Namespace] = Some(Namespace(event.namespace))

  given AvroEncoder[UserAdded] =
    val v1e = AvroEncoder[v1.UserAdded]
    val v2e = AvroEncoder[v2.UserAdded]
    AvroEncoder.basic { v =>
      v.fold(v1e.encode(v.schema), v2e.encode(v.schema))
    }

  given EventMessageDecoder[UserAdded] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.UserAdded.SCHEMA$
          qm.toMessage[v1.UserAdded](schema)
            .map(_.map(UserAdded.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.UserAdded.SCHEMA$
          qm.toMessage[v2.UserAdded](schema)
            .map(_.map(UserAdded.V2.apply))
    }

  given Show[UserAdded] = Show.show(_.fold(_.toString, _.toString))
