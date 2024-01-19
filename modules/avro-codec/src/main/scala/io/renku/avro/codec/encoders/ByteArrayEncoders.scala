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
import scodec.bits.ByteVector

import java.nio.ByteBuffer

trait ByteArrayEncoders:
  given AvroEncoder[ByteVector] = ByteArrayEncoders.forByteVector
  given AvroEncoder[Array[Byte]] = ByteArrayEncoders.forArray

object ByteArrayEncoders:
  val forByteVector: AvroEncoder[ByteVector] =
    AvroEncoder.curried { schema =>
      schema.getType match
        case Schema.Type.BYTES => _.toByteBuffer
        case Schema.Type.FIXED =>
          v => GenericData.get.createFixed(null, v.toArray, schema)
        case t =>
          throw AvroCodecException.encode(
            s"Cannot create decoder for ByteVector with schema $t"
          )
    }

  val forArray: AvroEncoder[Array[Byte]] =
    AvroEncoder.curried { schema =>
      schema.getType match
        case Schema.Type.BYTES => b => ByteBuffer.wrap(b)
        case Schema.Type.FIXED =>
          a =>
            val copy = new Array[Byte](schema.getFixedSize)
            System.arraycopy(a, 0, copy, 0, a.length)
            GenericData.get().createFixed(null, copy, schema)
        case t =>
          throw AvroCodecException.encode(
            s"Cannot create decoder for Array[Byte] with schema $t"
          )
    }
