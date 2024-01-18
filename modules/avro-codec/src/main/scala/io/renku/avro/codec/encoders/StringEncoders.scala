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
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.util.Utf8

import java.nio.ByteBuffer
import java.util.UUID

trait StringEncoders:
  given AvroEncoder[Utf8] = AvroEncoder.id[Utf8]
  given AvroEncoder[String] = StringEncoders.StringEncoder
  given AvroEncoder[CharSequence] = StringEncoders.StringEncoder.contramap(_.toString)
  given AvroEncoder[UUID] = StringEncoders.UUIDEncoder

object StringEncoders:
  object StringEncoder extends AvroEncoder[String]:
    override def encode(schema: Schema): String => Any = schema.getType match
      case Schema.Type.STRING if schema.getObjectProp("avro.java.string") == "String" =>
        AvroEncoder.id[String].encode(schema)
      case Schema.Type.STRING => Utf8Encoder.encode(schema)
      case Schema.Type.BYTES  => BytesEncoder.encode(schema)
      case Schema.Type.FIXED  => FixedStringEncoder.encode(schema)
      case _ =>
        throw AvroCodecException.encode(s"Unsupported type for string: $schema")

  object UUIDEncoder extends AvroEncoder[UUID]:
    def encode(schema: Schema): UUID => Any = uuid => new Utf8(uuid.toString)

  object Utf8Encoder extends AvroEncoder[String]:
    override def encode(schema: Schema): String => Any =
      v => new Utf8(v)

  object BytesEncoder extends AvroEncoder[String]:
    def encode(schema: Schema): String => Any = v => ByteBuffer.wrap(v.getBytes)

  object FixedStringEncoder extends AvroEncoder[String]:
    def encode(schema: Schema): String => Any = v =>
      if (v.getBytes.length > schema.getFixedSize)
        throw AvroCodecException.encode(
          s"Cannot write string with ${v.getBytes.length} bytes to fixed type ${schema.getFixedSize}"
        )
      else
        GenericData.get
          .createFixed(
            null,
            ByteBuffer.allocate(schema.getFixedSize).put(v.getBytes).array(),
            schema
          )
          .asInstanceOf[GenericData.Fixed]
