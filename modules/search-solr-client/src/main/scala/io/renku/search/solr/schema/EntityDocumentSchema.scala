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
    val discriminator: FieldName = FieldName("discriminator")
    val name: FieldName = FieldName("name")
    val description: FieldName = FieldName("description")

  val initialEntityDocumentAdd: Seq[SchemaCommand] = Seq(
    SchemaCommand.Add(FieldType.str(TypeName("discriminator"))),
    SchemaCommand.Add(FieldType.str(TypeName("name"))),
    SchemaCommand.Add(FieldType.text(TypeName("description"), Analyzer.classic)),
    SchemaCommand.Add(Field(Fields.discriminator, TypeName("discriminator"))),
    SchemaCommand.Add(Field(Fields.name, TypeName("name"))),
    SchemaCommand.Add(Field(Fields.description, TypeName("description")))
  )
