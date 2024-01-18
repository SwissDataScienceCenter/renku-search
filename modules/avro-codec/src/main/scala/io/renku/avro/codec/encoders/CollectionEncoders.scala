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

import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

trait CollectionEncoders {
  private def iterableEncoder[T, C[X] <: Iterable[X]](
      encoder: AvroEncoder[T]
  ): AvroEncoder[C[T]] = (schema: Schema) => {
    require(schema.getType == Schema.Type.ARRAY)
    val elementEncoder = encoder.encode(schema.getElementType)
    { t => t.map(elementEncoder.apply).toList.asJava }
  }

  given [T](using encoder: AvroEncoder[T], tag: ClassTag[T]): AvroEncoder[Array[T]] =
    (schema: Schema) => {
      require(schema.getType == Schema.Type.ARRAY)
      val elementEncoder = encoder.encode(schema.getElementType)
      { t => t.map(elementEncoder.apply).toList.asJava }
    }

  given [T](using encoder: AvroEncoder[T]): AvroEncoder[List[T]] = iterableEncoder(
    encoder
  )
  given [T](using encoder: AvroEncoder[T]): AvroEncoder[Seq[T]] = iterableEncoder(encoder)
  given [T](using encoder: AvroEncoder[T]): AvroEncoder[Set[T]] = iterableEncoder(encoder)
  given [T](using encoder: AvroEncoder[T]): AvroEncoder[Vector[T]] = iterableEncoder(
    encoder
  )

}
