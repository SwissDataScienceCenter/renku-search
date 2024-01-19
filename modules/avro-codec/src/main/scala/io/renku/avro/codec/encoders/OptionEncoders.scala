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

import io.renku.avro.codec.AvroEncoder
import org.apache.avro.Schema

trait OptionEncoders:
  given [T](using e: AvroEncoder[T]): AvroEncoder[Option[T]] =
    OptionEncoders.ForOption[T](e)

object OptionEncoders:
  final class ForOption[T](encoder: AvroEncoder[T]) extends AvroEncoder[Option[T]] {
    def encode(schema: Schema): Option[T] => Any = {
      // nullables must be encoded with a union of 2 elements, where null is the first type
      require(
        schema.getType == Schema.Type.UNION,
        "Options can only be encoded with a UNION schema"
      )
      require(
        schema.getTypes.size() >= 2,
        "Options can only be encoded with a union schema with 2 or more types"
      )
      require(schema.getTypes.get(0).getType == Schema.Type.NULL)
      val schemaSize = schema.getTypes.size()
      val elementSchema = schemaSize match
        case 2 => schema.getTypes.get(1)
        case _ => Schema.createUnion(schema.getTypes.subList(1, schemaSize))
      val elementEncoder = encoder.encode(elementSchema)
      { option => option.fold(null)(value => elementEncoder(value)) }
    }
  }
