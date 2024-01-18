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
import org.apache.avro.LogicalTypes.Decimal
import org.apache.avro.generic.GenericFixed
import org.apache.avro.{Conversions, Schema}

import java.nio.ByteBuffer

trait BigDecimalDecoders:
  given AvroDecoder[BigDecimal] = AvroDecoder.curried[BigDecimal] { schema =>
    schema.getType match
      case Schema.Type.BYTES  => BigDecimalDecoders.forBytes.decode(schema)
      case Schema.Type.STRING => BigDecimalDecoders.forString.decode(schema)
      case Schema.Type.FIXED  => BigDecimalDecoders.forFixed.decode(schema)
      case t =>
        throw AvroCodecException.decode(
          s"Unable to create decoder for BigDecimal with schema $t"
        )
  }

object BigDecimalDecoders:
  val forBytes: AvroDecoder[BigDecimal] = AvroDecoder { (schema, n) =>
    require(schema.getType == Schema.Type.BYTES)
    val logical = schema.getLogicalType.asInstanceOf[Decimal]
    val converter = new Conversions.DecimalConversion()

    n match {
      case bb: ByteBuffer => converter.fromBytes(bb, schema, logical)
      case bytes: Array[Byte] =>
        converter.fromBytes(ByteBuffer.wrap(bytes), schema, logical)
      case _ =>
        throw AvroCodecException.decode(
          s"Unable to decode '$n' to BigDecimal via ByteBuffer"
        )
    }
  }

  val forString: AvroDecoder[BigDecimal] =
    AvroDecoder { (schema, n) =>
      val decode = StringDecoders.forString.decode(schema)
      BigDecimal(decode(n))
    }

  val forFixed: AvroDecoder[BigDecimal] =
    AvroDecoder[BigDecimal] { (schema, n) =>
      require(schema.getType == Schema.Type.FIXED)

      val logical = schema.getLogicalType.asInstanceOf[Decimal]
      val converter = new Conversions.DecimalConversion()

      n match {
        case f: GenericFixed => converter.fromFixed(f, schema, logical)
        case _ =>
          throw AvroCodecException.decode(
            s"Unable to decode $n to BigDecimal via GenericFixed"
          )
      }
    }
