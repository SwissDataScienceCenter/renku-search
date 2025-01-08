package io.renku.search.events

import cats.syntax.all.*

import io.renku.avro.codec.{AvroDecoder, AvroReader}
import org.apache.avro.Schema
import scodec.bits.ByteVector

final case class QueueMessage(id: MessageId, header: MessageHeader, payload: ByteVector):

  def decodePayload[A: AvroDecoder](schema: Schema): Either[DecodeFailure, Seq[A]] =
    decodePayload(AvroReader(schema))

  def toMessage[A: AvroDecoder](schema: Schema): Either[DecodeFailure, EventMessage[A]] =
    decodePayload(schema).map(p => EventMessage(id, header, schema, p))

  private def decodePayload[A: AvroDecoder](
      avro: AvroReader
  ): Either[DecodeFailure, Seq[A]] =
    header.dataContentType match
      case DataContentType.Binary =>
        Either
          .catchNonFatal(avro.read[A](payload))
          .leftMap(DecodeFailure.AvroReadFailure.apply)
      case DataContentType.Json =>
        Either
          .catchNonFatal(avro.readJson[A](payload))
          .leftMap(DecodeFailure.AvroReadFailure.apply)
