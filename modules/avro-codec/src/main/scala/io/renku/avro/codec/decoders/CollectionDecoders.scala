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
import org.apache.avro.Schema

import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

trait CollectionDecoders:
  given [T](using dec: AvroDecoder[T]): AvroDecoder[List[T]] =
    CollectionDecoders.iterableDecoder[T, List](dec, _.toList)

  given [T](using dec: AvroDecoder[T]): AvroDecoder[Seq[T]] =
    CollectionDecoders.iterableDecoder[T, Seq](dec, _.toSeq)

  given [T](using dec: AvroDecoder[T]): AvroDecoder[Set[T]] =
    CollectionDecoders.iterableDecoder[T, Set](dec, _.toSet)

object CollectionDecoders:
  class ArrayDecoder[T: ClassTag](decoder: AvroDecoder[T]) extends AvroDecoder[Array[T]]:
    def decode(schema: Schema): Any => Array[T] = {
      require(
        schema.getType == Schema.Type.ARRAY,
        s"Require schema type ARRAY (was $schema)"
      )
      val decodeT = decoder.decode(schema.getElementType)
      {
        case array: Array[_]               => array.map(decodeT)
        case list: java.util.Collection[_] => list.asScala.map(decodeT).toArray
        case list: Iterable[_]             => list.map(decodeT).toArray
        case other =>
          throw AvroCodecException.decode(s"Unsupported array: $other")
      }
    }

  def iterableDecoder[T, C[X] <: Iterable[X]](
      decoder: AvroDecoder[T],
      build: Iterable[T] => C[T]
  ): AvroDecoder[C[T]] =
    AvroDecoder.curried[C[T]] { schema =>
      require(
        schema.getType == Schema.Type.ARRAY,
        s"Require schema type ARRAY (was $schema)"
      )
      val decodeT = decoder.decode(schema.getElementType)

      {
        case list: java.util.Collection[_] => build(list.asScala.map(decodeT))
        case list: Iterable[_]             => build(list.map(decodeT))
        case array: Array[_]               =>
          // converting array to Seq in order to avoid requiring ClassTag[T] as does arrayDecoder.
          build(array.toSeq.map(decodeT))
        case other =>
          throw AvroCodecException.decode(
            s"Unsupported collection type: $other"
          )
      }
    }
