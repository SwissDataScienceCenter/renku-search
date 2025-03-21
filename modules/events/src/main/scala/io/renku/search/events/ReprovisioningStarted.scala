package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.v2
import io.renku.search.model.*
import org.apache.avro.Schema

sealed trait ReprovisioningStarted extends RenkuEventPayload:
  val msgType = MsgType.ReprovisioningStarted
  def withId(id: Id): ReprovisioningStarted
  val schema: Schema = v2.ReprovisioningStarted.SCHEMA$
  val version: NonEmptyList[SchemaVersion] = NonEmptyList.of(SchemaVersion.V2)
  def fold[A](f: v2.ReprovisioningStarted => A): A

object ReprovisioningStarted:
  def apply(id: Id): ReprovisioningStarted = V2(v2.ReprovisioningStarted(id.value))

  final case class V2(event: v2.ReprovisioningStarted) extends ReprovisioningStarted:
    val id: Id = Id(event.id)
    def withId(id: Id): V2 = V2(event.copy(id = id.value))
    def fold[A](f: v2.ReprovisioningStarted => A): A = f(event)

  given AvroEncoder[ReprovisioningStarted] =
    val v2e = AvroEncoder[v2.ReprovisioningStarted]
    AvroEncoder.basic { v =>
      v.fold(v2e.encode(v.schema))
    }

  given EventMessageDecoder[ReprovisioningStarted] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          Left(DecodeFailure.VersionNotSupported(qm.id, qm.header))

        case SchemaVersion.V2 =>
          val schema = v2.ReprovisioningStarted.SCHEMA$
          qm.toMessage[v2.ReprovisioningStarted](schema)
            .map(_.map(ReprovisioningStarted.V2.apply))
    }

  given Show[ReprovisioningStarted] = Show.show(_.fold(_.toString))
