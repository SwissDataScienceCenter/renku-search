package io.renku.avro.codec.encoders

import io.renku.avro.codec.{AvroCodecException, AvroEncoder}
import org.apache.avro.LogicalTypes.{TimestampMicros, TimestampMillis}
import org.apache.avro.Schema

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneOffset}

trait DateTimeEncoders:
  given AvroEncoder[Instant] = DateTimeEncoders.ForInstant
  given AvroEncoder[LocalDateTime] = DateTimeEncoders.ForLocalDateTime
  given AvroEncoder[OffsetDateTime] = DateTimeEncoders.ForOffsetDateTime

object DateTimeEncoders:
  object ForOffsetDateTime extends AvroEncoder[OffsetDateTime]:
    override def encode(schema: Schema): OffsetDateTime => Any = { value =>
      value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

  object ForInstant extends TemporalWithLogicalTypeEncoder[Instant]:
    def epochMillis(temporal: Instant): Long = temporal.toEpochMilli
    def epochSeconds(temporal: Instant): Long = temporal.getEpochSecond
    def nanos(temporal: Instant): Long = temporal.getNano.toLong

  object ForLocalDateTime extends TemporalWithLogicalTypeEncoder[LocalDateTime]:
    def epochMillis(temporal: LocalDateTime): Long =
      temporal.toInstant(ZoneOffset.UTC).toEpochMilli
    def epochSeconds(temporal: LocalDateTime): Long =
      temporal.toEpochSecond(ZoneOffset.UTC)
    def nanos(temporal: LocalDateTime): Long = temporal.getNano.toLong

  abstract private[encoders] class TemporalWithLogicalTypeEncoder[T]
      extends AvroEncoder[T]:

    def epochMillis(temporal: T): Long
    def epochSeconds(temporal: T): Long
    def nanos(temporal: T): Long

    override def encode(schema: Schema): T => Any = {
      val toLong: T => Long = schema.getLogicalType match {
        case _: TimestampMillis => epochMillis
        case _: TimestampMicros => t => epochSeconds(t) * 1000000L + nanos(t) / 1000L
        case _ =>
          throw AvroCodecException.encode(
            s"Unsupported logical type for temporal: ${schema}"
          )
      }
      { value => java.lang.Long.valueOf(toLong(value)) }
    }
