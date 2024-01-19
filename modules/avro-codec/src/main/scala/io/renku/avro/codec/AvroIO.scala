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
import org.apache.avro.Schema
import scodec.bits.ByteVector

trait AvroIO extends AvroWriter with AvroReader

object AvroIO:
  def apply(schema: Schema): AvroIO =
    new AvroIO:
      private[this] val reader = AvroReader(schema)
      private[this] val writer = AvroWriter(schema)

      override def write[A: AvroEncoder](values: Seq[A]): ByteVector =
        writer.write(values)

      override def writeJson[A: AvroEncoder](values: Seq[A]): ByteVector =
        writer.writeJson(values)

      override def writeContainer[A: AvroEncoder](values: Seq[A]): ByteVector =
        writer.writeContainer(values)

      override def read[T: AvroDecoder](input: ByteVector): Seq[T] = reader.read(input)

      override def readJson[T: AvroDecoder](input: ByteVector): Seq[T] =
        reader.readJson(input)

      override def readContainer[T: AvroDecoder](input: ByteVector): Seq[T] =
        reader.readContainer(input)
