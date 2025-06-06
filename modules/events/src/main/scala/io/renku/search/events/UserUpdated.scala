package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.{v1, v2}
import io.renku.search.model.*
import org.apache.avro.Schema

sealed trait UserUpdated extends RenkuEventPayload:
  def fold[A](fv1: v1.UserUpdated => A, fv2: v2.UserUpdated => A): A
  def withId(id: Id): UserUpdated
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v1.UserUpdated.SCHEMA$, _ => v2.UserUpdated.SCHEMA$)
  val msgType: MsgType = MsgType.UserUpdated

object UserUpdated:
  def apply(
      id: Id,
      namespace: Namespace,
      firstName: Option[FirstName],
      lastName: Option[LastName],
      email: Option[Email]
  ): UserUpdated =
    V2(
      v2.UserUpdated(
        id.value,
        firstName.map(_.value),
        lastName.map(_.value),
        email.map(_.value),
        namespace.value
      )
    )

  final case class V1(event: v1.UserUpdated) extends UserUpdated:
    val id: Id = Id(event.id)
    def withId(id: Id): UserUpdated = V1(event.copy(id = id.value))
    def fold[A](fv1: v1.UserUpdated => A, fv2: v2.UserUpdated => A): A = fv1(event)
    val namespace: Option[Namespace] = None

  final case class V2(event: v2.UserUpdated) extends UserUpdated:
    val id: Id = Id(event.id)
    def withId(id: Id): UserUpdated = V2(event.copy(id = id.value))
    def fold[A](fv1: v1.UserUpdated => A, fv2: v2.UserUpdated => A): A = fv2(event)
    val namespace: Option[Namespace] = Some(Namespace(event.namespace))

  given AvroEncoder[UserUpdated] =
    val v1e = AvroEncoder[v1.UserUpdated]
    val v2e = AvroEncoder[v2.UserUpdated]
    AvroEncoder.basic { v =>
      v.fold(v1e.encode(v.schema), v2e.encode(v.schema))
    }

  given EventMessageDecoder[UserUpdated] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.UserUpdated.SCHEMA$
          qm.toMessage[v1.UserUpdated](schema)
            .map(_.map(UserUpdated.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.UserUpdated.SCHEMA$
          qm.toMessage[v2.UserUpdated](schema)
            .map(_.map(UserUpdated.V2.apply))
    }

  given Show[UserUpdated] = Show.show(_.fold(_.toString, _.toString))
