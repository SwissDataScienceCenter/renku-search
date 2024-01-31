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

trait EitherDecoders {

  given [A, B](using
      da: AvroDecoder[A],
      db: AvroDecoder[B],
      ta: TypeGuardedDecoding[A],
      tb: TypeGuardedDecoding[B]
  ): AvroDecoder[Either[A, B]] =
    AvroDecoder.curried[Either[A, B]] { schema =>
      require(schema.isUnion, s"Expected union type, but schema is not a union")
      require(
        schema.getTypes.size() == 2,
        s"Expected schema size 2, but got ${schema.getTypes.size()}"
      )

      val leftSchema = schema.getTypes.get(0)
      val rightSchema = schema.getTypes.get(1)

      { value =>
        if (ta.guard(leftSchema).isDefinedAt(value)) Left(da.decode(schema)(value))
        else if (tb.guard(rightSchema).isDefinedAt(value))
          Right(db.decode(schema)(value))
        else {
          val nameA = leftSchema.getFullName
          val nameB = rightSchema.getFullName
          throw AvroCodecException.decode(
            s"Could not decode $value into Either[$nameA, $nameB]"
          )
        }
      }
    }
}
