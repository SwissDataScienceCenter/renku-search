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
import io.renku.search.query.Query
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.*
import sttp.tapir.generic.Configuration
import sttp.tapir.{FieldName, Schema, SchemaType}

/** tapir schema definitions for the search api data structures */
trait ApiSchema extends ApiSchema.Primitives:
  given Schema[Query] = Schema.anyObject[Query]
  given Schema[QueryInput] = Schema.derived

  given Schema[User] = Schema
    .derived[User]
    .jsonExample(ApiSchema.exampleUser.widen)

  given Schema[Group] = Schema
    .derived[Group]
    .jsonExample(ApiSchema.exampleGroup.widen)

  given (using
      userSchema: Schema[User],
      groupSchema: Schema[Group]
  ): Schema[UserOrGroup] =
    Schema
      .derived[UserOrGroup]
      .withDiscriminator(
        SearchEntity.discriminatorField,
        Map("User" -> userSchema, "Group" -> groupSchema)
      )
      .jsonExample(ApiSchema.exampleGroup: UserOrGroup)

  given (using userSchema: Schema[User]): Schema[Project] = Schema
    .derived[Project]
    .modify(_.createdBy) { schemaOptUser =>
      // this is necessary to include the `type` property into the schema of the createdBy property
      // It is not added automagically, because we use the concrete type `User` and not `SearchEntity`
      // (the sealed trait).
      // Using `SearchEntity` results in a deadlock when evaluating the magnolia macros from tapir. I
      // tried to make all components lazy, but didn't manage to solve it
      val userType = userSchema.schemaType.asInstanceOf[SProduct[User]]
      val df = SProductField[User, String](
        FieldName("type"),
        Schema.string,
        _ => Some("User")
      )
      val nextUserSchema: Schema[User] =
        userSchema.copy(schemaType = userType.copy(fields = df :: userType.fields))
      schemaOptUser.copy(schemaType = SOption(nextUserSchema)(identity))
    }
    .jsonExample(ApiSchema.exampleProject.widen)

  given (using
      projectSchema: Schema[Project],
      userSchema: Schema[User],
      groupSchema: Schema[Group],
      ug: Schema[UserOrGroup]
  ): Schema[SearchEntity] =
    Schema
      .derived[SearchEntity]
      .withDiscriminator(
        SearchEntity.discriminatorField,
        Map("Project" -> projectSchema, "User" -> userSchema, "Group" -> groupSchema)
      )

  given Schema[FacetData] = {
    given Schema[Map[EntityType, Int]] = Schema.schemaForMap(_.name)
    Schema
      .derived[FacetData]
      .jsonExample(
        FacetData(
          Map(
            EntityType.Project -> 15,
            EntityType.User -> 3
          )
        )
      )
  }

  given Schema[PageDef] = Schema.derived
  given Schema[PageWithTotals] = Schema.derived
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
    given Schema[EntityType] = Schema.derivedEnumeration[EntityType].defaultStringBased

    extension [A](self: Schema[A])
      def withDiscriminator(property: String, subs: Map[String, Schema[?]]): Schema[A] =
        self.schemaType match {
          case s: SCoproduct[?] =>
            self.copy(schemaType =
              s.addDiscriminatorField(
                FieldName(property),
                Schema.string,
                subs.toList
                  .map { case (value, schema) =>
                    schema.name.map(SRef(_)).map(value -> _)
                  }
                  .flatten
                  .toMap
              )
            )
          case _ => self
        }
  end Primitives

  val exampleUser: SearchEntity.User = User(
    Id("1CAF4C73F50D4514A041C9EDDB025A36"),
    Some(Namespace("renku/renku")),
    Some(FirstName("Albert")),
    Some(LastName("Einstein")),
    Some(2.1)
  )

  val exampleGroup: SearchEntity.Group = Group(
    Id("2CAF4C73F50D4514A041C9EDDB025A36"),
    Name("SDSC"),
    Namespace("SDSC"),
    Some(Description("SDSC group")),
    Some(1.1)
  )

  val exampleProject: SearchEntity.Project = Project(
    id = Id("01HRA7AZ2Q234CDQWGA052F8MK"),
    name = Name("renku"),
    slug = Slug("renku"),
    namespace = Some(exampleGroup),
    repositories = Seq(Repository("https://github.com/renku")),
    visibility = Visibility.Public,
    description = Some(Description("Renku project")),
    createdBy = Some(exampleUser),
    creationDate = CreationDate(Instant.now),
    keywords = List(Keyword("data"), Keyword("science")),
    score = Some(1.0)
  )
end ApiSchema
