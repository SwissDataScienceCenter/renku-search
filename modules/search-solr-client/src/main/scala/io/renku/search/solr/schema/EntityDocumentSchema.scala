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

package io.renku.search.solr.schema

import io.renku.solr.client.schema.*

object EntityDocumentSchema:

  object Fields:
    val id: FieldName = FieldName("id")
    val entityType: FieldName = FieldName("_type")
    val name: FieldName = FieldName("name")
    val slug: FieldName = FieldName("slug")
    val repositories: FieldName = FieldName("repositories")
    val visibility: FieldName = FieldName("visibility")
    val description: FieldName = FieldName("description")
    val createdBy: FieldName = FieldName("createdBy")
    val creationDate: FieldName = FieldName("creationDate")
    val members: FieldName = FieldName("members")
    val nestPath: FieldName = FieldName("_nest_path_")
    val root: FieldName = FieldName("_root_")
    val nestParent: FieldName = FieldName("_nest_parent_")
    // catch-all field
    val contentAll: FieldName = FieldName("content_all")

  object FieldTypes:
    val string: FieldType = FieldType.str(TypeName("SearchString")).makeDocValue
    val text: FieldType = FieldType.text(TypeName("SearchText"), Analyzer.classic)
    val textAll: FieldType = FieldType.text(TypeName("SearchTextAll"), Analyzer.classic).makeMultiValued
    val dateTime: FieldType = FieldType.dateTimePoint(TypeName("SearchDateTime"))

  val initialEntityDocumentAdd: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(FieldTypes.string),
    SchemaCommand.Add(FieldTypes.text),
    SchemaCommand.Add(FieldTypes.dateTime),
    SchemaCommand.Add(Field(Fields.entityType, FieldTypes.string)),
    SchemaCommand.Add(Field(Fields.name, FieldTypes.string)),
    SchemaCommand.Add(Field(Fields.slug, FieldTypes.string)),
    SchemaCommand.Add(Field(Fields.repositories, FieldTypes.string).makeMultiValued),
    SchemaCommand.Add(Field(Fields.visibility, FieldTypes.string)),
    SchemaCommand.Add(Field(Fields.description, FieldTypes.text)),
    SchemaCommand.Add(Field(Fields.createdBy, FieldType.nestedPath)),
    SchemaCommand.Add(Field(Fields.creationDate, FieldTypes.dateTime)),
    SchemaCommand.Add(Field(Fields.members, FieldType.nestedPath).makeMultiValued),
    SchemaCommand.Add(Field(Fields.nestParent, FieldTypes.string))
  )

  val copyContentField: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(FieldTypes.textAll),
    SchemaCommand.Add(Field(Fields.contentAll, FieldTypes.textAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.name, Fields.contentAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.description, Fields.contentAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.slug, Fields.contentAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.repositories, Fields.contentAll)),
    SchemaCommand.Add(CopyFieldRule(Fields.visibility, Fields.contentAll))
  )
