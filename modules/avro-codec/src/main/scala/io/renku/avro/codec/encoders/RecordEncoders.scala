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

import scala.compiletime.*
import scala.deriving.*
import scala.jdk.CollectionConverters.*

trait RecordEncoders:
  inline given [T <: Product](using A: Mirror.ProductOf[T]): AvroEncoder[T] =
    RecordEncoders.Macros.deriveViaMirror[T]

object RecordEncoders {
  final inline def derived[A <: Product](using
      inline A: Mirror.ProductOf[A]
  ): AvroEncoder[A] =
    RecordEncoders.Macros.deriveViaMirror[A]

  private object Macros {

    inline given deriveViaMirror[A <: Product](using
        m: Mirror.ProductOf[A]
    ): AvroEncoder[A] =
      val encoders = summonAll[m.MirroredElemTypes]
      val names = getElemLabels[m.MirroredElemLabels]
      AvroEncoder { (schema, a) =>
        val t = a.asInstanceOf[Product].productIterator.toList
        val fields = schema.getFields.asScala.toSeq.map { f =>
          val fidx = names.indexOf(f.name())
          val scalaValue = t(fidx)
          val enc = encoders(fidx).asInstanceOf[AvroEncoder[Any]]
          enc.encode(f.schema())(scalaValue)
        }
        AvroRecord(schema, fields)
      }

    inline def summonAll[T <: Tuple]: List[AvroEncoder[?]] =
      inline erasedValue[T] match
        case _: EmptyTuple => Nil
        case _: (t *: ts)  => summonInline[AvroEncoder[t]] :: summonAll[ts]

    inline def getElemLabels[A <: Tuple]: List[String] =
      inline erasedValue[A] match
        case _: EmptyTuple => Nil
        case _: (t *: ts)  => constValue[t].toString :: getElemLabels[ts]
  }
}
