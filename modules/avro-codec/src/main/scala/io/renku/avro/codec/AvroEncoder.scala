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

import io.renku.avro.codec.encoders.RecordEncoders
import org.apache.avro.Schema

import scala.deriving.Mirror

trait AvroEncoder[T] { self =>

  def encode(schema: Schema): T => Any

  final def contramap[U](f: U => T): AvroEncoder[U] =
    AvroEncoder.curried[U](schema => u => self.encode(schema).apply(f(u)))

}

object AvroEncoder:
  def apply[T](f: (Schema, T) => Any): AvroEncoder[T] = (schema: Schema) => f(schema, _)
  def curried[T](f: Schema => T => Any): AvroEncoder[T] = (schema: Schema) => f(schema)
  def basic[T](f: T => Any): AvroEncoder[T] = (_: Schema) => t => f(t)
  def id[T]: AvroEncoder[T] = AvroEncoder.basic(identity)

  def apply[T](using enc: AvroEncoder[T]): AvroEncoder[T] = enc

  final inline def derived[A <: Product](using
      inline A: Mirror.ProductOf[A]
  ): AvroEncoder[A] =
    RecordEncoders.derived[A]
