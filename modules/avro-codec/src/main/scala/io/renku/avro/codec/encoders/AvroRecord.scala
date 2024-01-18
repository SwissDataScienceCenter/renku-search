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

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.avro.specific.SpecificRecord

private[encoders] trait AvroRecord extends GenericRecord with SpecificRecord

private[encoders] object AvroRecord:
  def apply(schema: Schema, values: Seq[Any]): Immutable =
    Immutable(schema, values)

  final case class Immutable(schema: Schema, values: Seq[Any]) extends AvroRecord:
    override def put(key: String, v: Any) = sys.error("immutable record")
    override def put(i: Int, v: Any) = sys.error("Immutable record")
    override def get(key: String): Any = {
      val field = schema.getField(key)
      if (field == null)
        sys.error(s"Field $key does not exist in record schema=$schema, values=$values")
      else values(field.pos())
    }
    override def get(i: Int) = values(i)
    override def getSchema = schema
