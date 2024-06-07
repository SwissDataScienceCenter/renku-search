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

package io.renku.search.api.tapir

import java.time.Instant

import io.renku.search.api.data.*
import io.renku.search.api.data.SearchEntity.*
import io.renku.search.api.tapir.SchemaSyntax.*
import io.renku.search.model.*
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.*
import sttp.tapir.generic.Configuration
import sttp.tapir.{FieldName, Schema, SchemaType}

/** tapir schema definitions for the search api data structures */
trait ApiSchema extends ApiSchema.Primitives:
  given Schema[User] = Schema
    .derived[User]
    .jsonExample(ApiSchema.exampleUser)

  given Schema[Group] = Schema
    .derived[Group]
    .jsonExample(ApiSchema.exampleGroup)

  given Schema[Project] = Schema
    .derived[Project]
    .jsonExample(ApiSchema.exampleProject)

  given Schema[UserId] = Schema
    .derived[UserId]
    .jsonExample(UserId(Id("01HRA7AZ2Q234CDQWGA052F8MK")))

  given (using
      projectSchema: Schema[Project],
      userSchema: Schema[User],
      groupSchema: Schema[Group]
  ): Schema[SearchEntity] = {
    val derived = Schema.derived[SearchEntity]
    derived.schemaType match {
      case s: SCoproduct[_] =>
        derived.copy(schemaType =
          s.addDiscriminatorField(
            FieldName(SearchEntity.discriminatorField),
            Schema.string,
            List(
              projectSchema.name.map(SRef(_)).map("Project" -> _),
              userSchema.name.map(SRef(_)).map("User" -> _),
              groupSchema.name.map(SRef(_)).map("Group" -> _)
            ).flatten.toMap
          )
        )
      case s => derived
    }
  }

  given Schema[SearchResult] = Schema.derived
end ApiSchema

object ApiSchema:
  trait Primitives:
    given Schema[Id] = Schema.string[Id]
    given Schema[Name] = Schema.string[Name]
    given Schema[Namespace] = Schema.string[Namespace]
    given Schema[Slug] = Schema.string[Slug]
    given Schema[Repository] = Schema.string[Repository]
    given Schema[Visibility] =
      Schema.derivedEnumeration[Visibility].defaultStringBased
    given Schema[Description] = Schema.string[Description]
    given Schema[CreationDate] = Schema(SDateTime())
    given Schema[Keyword] = Schema.string[Keyword]
    given Schema[FirstName] = Schema.string[FirstName]
    given Schema[LastName] = Schema.string[LastName]
    given Schema[Email] = Schema.string[Email]
  end Primitives

  val exampleUser: SearchEntity = User(
    Id("1CAF4C73F50D4514A041C9EDDB025A36"),
    Some(Namespace("renku/renku")),
    Some(FirstName("Albert")),
    Some(LastName("Einstein")),
    Some(2.1)
  )

  val exampleGroup: SearchEntity = Group(
    Id("2CAF4C73F50D4514A041C9EDDB025A36"),
    Name("SDSC"),
    Namespace("SDSC"),
    Some(Description("SDSC group")),
    Some(1.1)
  )

  val exampleProject: SearchEntity = Project(
    Id("01HRA7AZ2Q234CDQWGA052F8MK"),
    Name("renku"),
    Slug("renku"),
    Some(Namespace("renku/renku")),
    Seq(Repository("https://github.com/renku")),
    Visibility.Public,
    Some(Description("Renku project")),
    UserId(Id("bla")),
    CreationDate(Instant.now),
    List(Keyword("data"), Keyword("science")),
    Some(1.0)
  )
end ApiSchema
