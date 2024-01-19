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

import io.renku.avro.codec.AvroDecoder
import org.apache.avro.Schema

trait OptionDecoders:
  given [T](using dec: AvroDecoder[T]): AvroDecoder[Option[T]] =
    OptionDecoders.ForOption[T](dec)

object OptionDecoders:
  final class ForOption[T](decoder: AvroDecoder[T]) extends AvroDecoder[Option[T]] {
    override def decode(schema: Schema): Any => Option[T] = {
      // nullables must be encoded with a union of 2 elements, where null is the first type
      require(
        schema.getType == Schema.Type.UNION,
        s"Options can only be encoded with a UNION schema, schema=$schema"
      )
      require(
        schema.getTypes.size() >= 2,
        "An option should be encoded with a UNION schema with at least 2 element types"
      )
      require(
        schema.getTypes.get(0).getType == Schema.Type.NULL,
        "Options can only be encoded with a UNION schema with NULL as the first element type"
      )
      val schemaSize = schema.getTypes.size()
      val elementSchema = schemaSize match
        case 2 => schema.getTypes.get(1)
        case _ => Schema.createUnion(schema.getTypes.subList(1, schemaSize))
      val decode = decoder.decode(elementSchema)
      { value => if (value == null) None else Some(decode(value)) }
    }

  }
