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

package io.renku.solr.client.schema

import io.bullet.borer.Encoder

opaque type FieldTypeClass = String

object FieldTypeClass:
  def apply(name: String): FieldTypeClass = name

  extension (self: FieldTypeClass) def name: String = self

  object Defaults:
    val intPointField: FieldTypeClass = "IntPointField"
    val longPointField: FieldTypeClass = "LongPointField"
    val floatPointField: FieldTypeClass = "FloatPointField"
    val doublePointField: FieldTypeClass = "DoublePointField"
    val textField: FieldTypeClass = "TextField"
    val strField: FieldTypeClass = "StrField"
    val uuidField: FieldTypeClass = "UUIDField"
    val rankField: FieldTypeClass = "RankField"
    val datePointField: FieldTypeClass = "DatePointField"
    val dateRangeField: FieldTypeClass = "DateRangeField"
    val boolField: FieldTypeClass = "BoolField"
    val binaryField: FieldTypeClass = "BinaryField"
    val nestedPath: FieldTypeClass = "solr.NestPathField"

  given Encoder[FieldTypeClass] = Encoder.forString
