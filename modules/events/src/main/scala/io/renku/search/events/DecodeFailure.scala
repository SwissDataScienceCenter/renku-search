package io.renku.search.events

import cats.data.NonEmptyList

import io.renku.events.Header
import scodec.bits.ByteVector

trait DecodeFailure extends RuntimeException

object DecodeFailure:
  abstract private[events] class NoStackTrace(message: String)
      extends RuntimeException(message)
      with DecodeFailure:
    override def fillInStackTrace(): Throwable = this

  final case class AvroReadFailure(cause: Throwable)
      extends RuntimeException(cause)
      with DecodeFailure

  final case class VersionNotSupported(id: MessageId, header: MessageHeader)
      extends NoStackTrace(
        s"Version ${header.schemaVersion} not supported for payload in message $id (header: $header)"
      )

  final case class HeaderReadError(
      data: ByteVector,
      causeBinary: Throwable,
      causeJson: Throwable
  ) extends NoStackTrace(
        s"Reading message header failed! Binary-Error: ${causeBinary.getMessage} Json-Error: ${causeJson.getMessage}"
      ):
    addSuppressed(causeBinary)
    addSuppressed(causeJson)

  final case class NoHeaderRecord(data: ByteVector)
      extends NoStackTrace(
        s"No header record found in byte vector: $data"
      )

  final case class MultipleHeaderRecords(data: ByteVector, headers: NonEmptyList[Header])
      extends NoStackTrace(
        s"Multiple header records (${headers.size}) found. Required exactly one."
      )

  final case class FieldReadError(fieldName: String, value: String, message: String)
      extends NoStackTrace(
        s"Reading field '$fieldName' with value '$value' failed: $message"
      )
