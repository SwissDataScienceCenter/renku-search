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
import org.apache.avro.Schema
import org.apache.avro.generic.IndexedRecord

import scala.compiletime.*
import scala.deriving.*
import scala.jdk.CollectionConverters.*

trait RecordDecoders:
  inline given [T <: Product](using A: Mirror.ProductOf[T]): AvroDecoder[T] =
    RecordDecoders.Macros.deriveViaMirror[T]

object RecordDecoders:

  final inline def derived[A <: Product](using
      inline A: Mirror.ProductOf[A]
  ): AvroDecoder[A] =
    RecordDecoders.Macros.deriveViaMirror[A]

  private object Macros {

    inline given deriveViaMirror[A <: Product](using
        m: Mirror.ProductOf[A]
    ): AvroDecoder[A] =
      val decoders = summonAll[m.MirroredElemTypes]
      AvroDecoder { (schema, a) =>
        require(schema.getType == Schema.Type.RECORD)
        a match
          case r: IndexedRecord =>
            val len = decoders.length
            var i = len - 1
            var result: Tuple = EmptyTuple
            while (i >= 0) {
              val fieldSchema = schema.getFields.get(i).schema()
              val decoded = decoders(i).decode(fieldSchema).apply(r.get(i))
              result = decoded *: result
              i = i - 1
            }
            m.fromTuple(result.asInstanceOf[m.MirroredElemTypes])

          case _ =>
            throw AvroCodecException.decode(
              s"This record decoder can only handle IndexedRecords, was ${a.getClass}"
            )
      }

    inline def summonAll[T <: Tuple]: List[AvroDecoder[?]] =
      inline erasedValue[T] match
        case _: EmptyTuple => Nil
        case _: (t *: ts)  => summonInline[AvroDecoder[t]] :: summonAll[ts]
  }
