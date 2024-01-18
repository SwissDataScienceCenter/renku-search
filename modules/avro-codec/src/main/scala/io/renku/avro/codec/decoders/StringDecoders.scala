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
import org.apache.avro.generic.GenericFixed
import org.apache.avro.util.Utf8

import java.nio.ByteBuffer
import java.util.UUID

trait StringDecoders:
  given AvroDecoder[String] = StringDecoders.forString
  given AvroDecoder[Utf8] = StringDecoders.forUtf8
  given AvroDecoder[UUID] = StringDecoders.forString.map(UUID.fromString)
  given AvroDecoder[CharSequence] = StringDecoders.forString.map(identity)

object StringDecoders:
  val forString: AvroDecoder[String] = AvroDecoder.basic[String] {
    case s: Utf8         => s.toString
    case s: String       => s
    case s: CharSequence => s.toString
    case b: Array[Byte]  => new Utf8(b).toString
    case b: ByteBuffer   => new Utf8(b.array).toString
    case f: GenericFixed => new Utf8(f.bytes()).toString
    case v =>
      throw AvroCodecException.decode(
        s"Unsupported type $v (${v.getClass}) for StringDecoder"
      )
  }

  val forUtf8: AvroDecoder[Utf8] = AvroDecoder.basic[Utf8] {
    case utf8: Utf8          => utf8
    case string: String      => new Utf8(string)
    case b: Array[Byte]      => new Utf8(b)
    case bytes: ByteBuffer   => new Utf8(bytes.array())
    case fixed: GenericFixed => new Utf8(fixed.bytes())
  }
