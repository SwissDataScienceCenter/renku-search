package io.renku.search.events

import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.{v1, v2}
import io.renku.search.model.Id
import org.apache.avro.Schema

final case class UserRemoved(id: Id) extends RenkuEventPayload:
  def withId(id: Id): UserRemoved = copy(id = id)
  def version: NonEmptyList[SchemaVersion] = SchemaVersion.all
  val schema: Schema = v2.UserRemoved.SCHEMA$
  val msgType: MsgType = MsgType.UserRemoved

object UserRemoved:

  given AvroEncoder[UserRemoved] =
    val v2e = AvroEncoder[v2.UserRemoved]
    AvroEncoder.basic { v =>
      val event = v2.UserRemoved(v.id.value)
      v2e.encode(v.schema)(event)
    }

  given EventMessageDecoder[UserRemoved] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.UserRemoved.SCHEMA$
          qm.toMessage[v1.UserRemoved](schema)
            .map(_.map(e => UserRemoved(Id(e.id))))

        case SchemaVersion.V2 =>
          val schema = v2.UserRemoved.SCHEMA$
          qm.toMessage[v2.UserRemoved](schema)
            .map(_.map(e => UserRemoved(Id(e.id))))
    }
