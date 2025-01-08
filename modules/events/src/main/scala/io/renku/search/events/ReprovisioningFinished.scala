package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.v2
import io.renku.search.model.*
import org.apache.avro.Schema

sealed trait ReprovisioningFinished extends RenkuEventPayload:
  val msgType = MsgType.ReprovisioningFinished
  def withId(id: Id): ReprovisioningFinished
  val schema: Schema = v2.ReprovisioningFinished.SCHEMA$
  val version: NonEmptyList[SchemaVersion] = NonEmptyList.of(SchemaVersion.V2)
  def fold[A](f: v2.ReprovisioningFinished => A): A

object ReprovisioningFinished:
  def apply(id: Id): ReprovisioningFinished = V2(v2.ReprovisioningFinished(id.value))

  final case class V2(event: v2.ReprovisioningFinished) extends ReprovisioningFinished:
    val id: Id = Id(event.id)
    def withId(id: Id): V2 = V2(event.copy(id = id.value))
    def fold[A](f: v2.ReprovisioningFinished => A): A = f(event)

  given AvroEncoder[ReprovisioningFinished] =
    val v2e = AvroEncoder[v2.ReprovisioningFinished]
    AvroEncoder.basic { v =>
      v.fold(v2e.encode(v.schema))
    }

  given EventMessageDecoder[ReprovisioningFinished] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          Left(DecodeFailure.VersionNotSupported(qm.id, qm.header))

        case SchemaVersion.V2 =>
          val schema = v2.ReprovisioningFinished.SCHEMA$
          qm.toMessage[v2.ReprovisioningFinished](schema)
            .map(_.map(ReprovisioningFinished.V2.apply))
    }

  given Show[ReprovisioningFinished] = Show.show(_.fold(_.toString))
