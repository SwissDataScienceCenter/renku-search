/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.renku.avro.codec.encoders

import io.renku.avro.codec.{AvroEncoder, AvroCodecException}
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
