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

package io.renku.avro.codec.decoders

import io.renku.avro.codec.{AvroDecoder, AvroCodecException}
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
