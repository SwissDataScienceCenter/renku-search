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

import io.renku.avro.codec.{AvroCodecException, AvroDecoder}
import org.apache.avro.generic.GenericFixed
import scodec.bits.{BitVector, ByteVector}

import java.nio.ByteBuffer

trait ByteArrayDecoders:
  given AvroDecoder[ByteVector] = ByteArrayDecoders.forByteVector
  given AvroDecoder[BitVector] = ByteArrayDecoders.forByteVector.map(_.bits)
  given AvroDecoder[Array[Byte]] = ByteArrayDecoders.forByteArray

object ByteArrayDecoders:
  val forByteVector: AvroDecoder[ByteVector] = AvroDecoder.basic[ByteVector] {
    case b: ByteBuffer   => ByteVector.view(b)
    case b: Array[Byte]  => ByteVector.view(b)
    case f: GenericFixed => ByteVector.view(f.bytes())
    case v =>
      throw AvroCodecException.decode(
        s"ByteVectorDecoder cannot decode $v"
      )
  }

  val forByteArray: AvroDecoder[Array[Byte]] = AvroDecoder.basic[Array[Byte]] {
    case b: ByteBuffer   => b.array()
    case b: Array[Byte]  => b
    case f: GenericFixed => f.bytes()
    case v =>
      throw AvroCodecException.decode(
        s"ByteArrayDecoder cannot decode $v"
      )
  }
