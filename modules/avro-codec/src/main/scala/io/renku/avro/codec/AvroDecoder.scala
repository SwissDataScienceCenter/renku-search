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

package io.renku.avro.codec

import io.renku.avro.codec.decoders.RecordDecoders
import org.apache.avro.Schema

import scala.deriving.Mirror

trait AvroDecoder[T] { self =>

  def decode(schema: Schema): Any => T

  final def map[U](f: T => U): AvroDecoder[U] =
    AvroDecoder.curried[U](schema => in => f(self.decode(schema).apply(in)))
}

object AvroDecoder:
  def apply[T](f: (Schema, Any) => T): AvroDecoder[T] = (schema: Schema) => f(schema, _)
  def curried[T](f: Schema => Any => T): AvroDecoder[T] = (schema: Schema) => f(schema)
  def basic[T](f: Any => T): AvroDecoder[T] = apply[T]((_, in) => f(in))

  def apply[T](using dec: AvroDecoder[T]): AvroDecoder[T] = dec

  inline def derived[A <: Product](using
      inline A: Mirror.ProductOf[A]
  ): AvroDecoder[A] =
    RecordDecoders.derived[A]
