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

final case class FieldType(
    name: TypeName,
    `class`: FieldTypeClass,
    analyzer: Option[Analyzer] = None,
    required: Boolean = false,
    indexed: Boolean = true,
    stored: Boolean = true,
    multiValued: Boolean = false,
    uninvertible: Boolean = false,
    docValues: Boolean = false,
    sortMissingLast: Boolean = true
):
  lazy val makeDocValue: FieldType = copy(docValues = true)

object FieldType:

  def text(name: TypeName, analyzer: Analyzer): FieldType =
    FieldType(name, FieldTypeClass.Defaults.textField, analyzer = Some(analyzer))

  def str(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.strField)

  def int(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.intPointField)

  def long(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.longPointField)

  def double(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.doublePointField)

  def dateTimeRange(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.dateRangeField)

  def dateTimePoint(name: TypeName): FieldType =
    FieldType(name, FieldTypeClass.Defaults.datePointField)

  lazy val nestedPath: FieldType =
    FieldType(TypeName("_nest_path_"), FieldTypeClass.Defaults.nestedPath)
