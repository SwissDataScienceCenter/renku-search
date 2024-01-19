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

trait PrimitiveDecoders {

  given AvroDecoder[Byte] = AvroDecoder.basic[Byte] {
    case b: Byte => b
    case v       => v.asInstanceOf[Int].byteValue()
  }

  given AvroDecoder[Short] = AvroDecoder.basic[Short] {
    case b: Byte  => b
    case s: Short => s
    case i: Int   => i.toShort
  }

  given AvroDecoder[Int] = AvroDecoder.basic[Int] {
    case b: Byte  => b
    case s: Short => s
    case i: Int   => i
    case other    => throw AvroCodecException.decode(s"Cannot convert $other to INT")
  }

  given AvroDecoder[Long] = AvroDecoder.basic[Long] {
    case b: Byte  => b
    case s: Short => s
    case i: Int   => i
    case l: Long  => l
    case other    => throw AvroCodecException.decode(s"Cannot convert $other to LONG")
  }

  given AvroDecoder[Double] = AvroDecoder.basic[Double] {
    case d: Double           => d
    case d: java.lang.Double => d
  }

  given AvroDecoder[Float] = AvroDecoder.basic[Float] {
    case d: Float           => d
    case d: java.lang.Float => d
  }

  given AvroDecoder[Boolean] = AvroDecoder.basic[Boolean] {
    _.asInstanceOf[Boolean]
  }
}
