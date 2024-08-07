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

import io.renku.solr.client.schema.SchemaCommand.DeleteDynamicField

enum SchemaCommand:
  case Add(element: SchemaCommand.Element)
  case DeleteField(name: FieldName)
  case DeleteType(name: TypeName)
  case DeleteDynamicField(name: FieldName)
  case Replace(element: SchemaCommand.ReplaceElem)

  def commandName: String = this match
    case Add(_: Field)                => "add-field"
    case Add(_: FieldType)            => "add-field-type"
    case Add(_: DynamicFieldRule)     => "add-dynamic-field"
    case Add(_: CopyFieldRule)        => "add-copy-field"
    case Replace(_: Field)            => "replace-field"
    case Replace(_: FieldType)        => "replace-field-type"
    case Replace(_: DynamicFieldRule) => "replace-dynamic-field"
    case _: DeleteField               => "delete-field"
    case _: DeleteType                => "delete-field-type"
    case _: DeleteDynamicField        => "delete-dynamic-field"

object SchemaCommand:
  type Element = FieldType | Field | DynamicFieldRule | CopyFieldRule
  type ReplaceElem = FieldType | Field | DynamicFieldRule
