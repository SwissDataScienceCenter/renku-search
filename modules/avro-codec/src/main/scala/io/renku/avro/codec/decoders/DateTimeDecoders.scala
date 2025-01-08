package io.renku.avro.codec.decoders

import io.renku.avro.codec.{AvroCodecException, AvroDecoder}
import org.apache.avro.LogicalTypes.{TimestampMicros, TimestampMillis}
import org.apache.avro.Schema

import java.time.Instant

trait DateTimeDecoders:
  given AvroDecoder[Instant] = DateTimeDecoders.forInstant

object DateTimeDecoders:
  val forInstant: AvroDecoder[Instant] = new TemporalWithLogicalTypeDecoder[Instant]:
    override def ofEpochMillis(millis: Long) = Instant.ofEpochMilli(millis)
    override def ofEpochSeconds(seconds: Long, nanos: Long) =
      Instant.ofEpochSecond(seconds, nanos)

  abstract private class TemporalWithLogicalTypeDecoder[T] extends AvroDecoder[T] {
    def ofEpochMillis(millis: Long): T
    def ofEpochSeconds(seconds: Long, nanos: Long): T

    override def decode(schema: Schema): Any => T = {
      val decoder: Any => T = schema.getLogicalType match {
        case _: TimestampMillis => {
          case l: Long => ofEpochMillis(l)
          case i: Int  => ofEpochMillis(i.toLong)
        }
        case _: TimestampMicros => {
          case l: Long => ofEpochSeconds(l / 1000000, l % 1000000 * 1000)
          case i: Int  => ofEpochSeconds(i / 1000000, i % 1000000 * 1000)
        }
        case _ =>
          throw AvroCodecException.decode(
            s"Unsupported logical type for temporal: ${schema}"
          )
      }

      { value => decoder(value) }
    }
  }
