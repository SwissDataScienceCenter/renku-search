package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.{v1, v2}
import io.renku.search.model.Id
import org.apache.avro.Schema

final case class ProjectRemoved(id: Id) extends RenkuEventPayload:
  val version: NonEmptyList[SchemaVersion] = SchemaVersion.all
  val schema: Schema = v2.ProjectRemoved.SCHEMA$
  val msgType: MsgType = MsgType.ProjectRemoved

object ProjectRemoved:
  given Show[ProjectRemoved] = Show.fromToString

  given AvroEncoder[ProjectRemoved] =
    val v2e = AvroEncoder[v2.ProjectRemoved]
    AvroEncoder.basic { v =>
      val event = v2.ProjectRemoved(v.id.value)
      v2e.encode(v.schema)(event)
    }

  given EventMessageDecoder[ProjectRemoved] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.ProjectRemoved.SCHEMA$
          qm.toMessage[v1.ProjectRemoved](schema)
            .map(_.map(e => ProjectRemoved(Id(e.id))))

        case SchemaVersion.V2 =>
          val schema = v2.ProjectRemoved.SCHEMA$
          qm.toMessage[v2.ProjectRemoved](schema)
            .map(_.map(e => ProjectRemoved(Id(e.id))))
    }
