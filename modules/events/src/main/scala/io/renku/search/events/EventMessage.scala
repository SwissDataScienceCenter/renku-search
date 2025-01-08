package io.renku.search.events

import cats.effect.Sync
import cats.syntax.all.*

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.AvroWriter
import org.apache.avro.Schema
import scodec.bits.ByteVector

final case class EventMessage[P](
    id: MessageId,
    header: MessageHeader,
    payloadSchema: Schema,
    payload: Seq[P]
):
  private lazy val payloadWriter = AvroWriter(payloadSchema)

  def toAvro(using AvroEncoder[P]): EventMessage.AvroPayload =
    val h = header.toAvro
    val b = header.dataContentType match
      case DataContentType.Binary => payloadWriter.write(payload)
      case DataContentType.Json   => payloadWriter.writeJson(payload)
    EventMessage.AvroPayload(h, b)

  def map[B](f: P => B): EventMessage[B] =
    EventMessage(id, header, payloadSchema, payload.map(f))

  def withPayload(pl: Seq[P]): EventMessage[P] = copy(payload = pl)

  def modifyHeader(f: MessageHeader => MessageHeader): EventMessage[P] =
    copy(header = f(header))

object EventMessage:
  def create[F[_]: Sync, A <: RenkuEventPayload](
      id: MessageId,
      src: MessageSource,
      ct: DataContentType,
      reqId: RequestId,
      payload: A
  ): F[EventMessage[A]] =
    MessageHeader
      .create(src, payload.msgType, ct, payload.version.head, reqId)
      .map(h => EventMessage(id, h, payload.schema, Seq(payload)))

  final case class AvroPayload(header: ByteVector, payload: ByteVector)
