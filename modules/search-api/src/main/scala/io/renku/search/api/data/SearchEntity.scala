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

package io.renku.search.api.data

import java.time.Instant

import io.bullet.borer.NullOptions.given
import io.bullet.borer.*
import io.bullet.borer.derivation.MapBasedCodecs.{deriveAllCodecs, deriveCodec}
import io.renku.search.api.tapir.SchemaSyntax.*
import io.renku.search.model.*
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType._
import sttp.tapir.generic.Configuration
import sttp.tapir.{FieldName, Schema, SchemaType}

sealed trait SearchEntity

final case class Project(
    id: Id,
    name: Name,
    slug: projects.Slug,
    repositories: Seq[projects.Repository],
    visibility: projects.Visibility,
    description: Option[projects.Description] = None,
    createdBy: UserId,
    creationDate: projects.CreationDate,
    keywords: List[Keyword] = Nil,
    score: Option[Double] = None
) extends SearchEntity

object Project:
  private given Schema[Id] = Schema.string[Id]
  private given Schema[Name] = Schema.string[Name]
  private given Schema[projects.Slug] = Schema.string[projects.Slug]
  private given Schema[projects.Repository] = Schema.string[projects.Repository]
  private given Schema[projects.Visibility] =
    Schema.derivedEnumeration[projects.Visibility].defaultStringBased
  private given Schema[projects.Description] = Schema.string[projects.Description]
  private given Schema[projects.CreationDate] = Schema(SDateTime())
  private given Schema[Keyword] = Schema.string[Keyword]
  given Schema[Project] = Schema
    .derived[Project]
    .jsonExample(
      Project(
        Id("01HRA7AZ2Q234CDQWGA052F8MK"),
        Name("renku"),
        projects.Slug("renku"),
        Seq(projects.Repository("https://github.com/renku")),
        projects.Visibility.Public,
        Some(projects.Description("Renku project")),
        UserId(Id("1CAF4C73F50D4514A041C9EDDB025A36")),
        projects.CreationDate(Instant.now),
        List(Keyword("data"), Keyword("science")),
        Some(1.0)
      ): SearchEntity
    )

final case class UserId(id: Id)
object UserId:
  given Codec[UserId] = deriveCodec[UserId]

  private given Schema[Id] = Schema.string[Id]
  given Schema[UserId] = Schema
    .derived[UserId]
    .jsonExample(UserId(Id("01HRA7AZ2Q234CDQWGA052F8MK")))

final case class User(
    id: Id,
    firstName: Option[users.FirstName] = None,
    lastName: Option[users.LastName] = None,
    score: Option[Double] = None
) extends SearchEntity

object User:
  private given Schema[Id] = Schema.string[Id]
  private given Schema[users.FirstName] = Schema.string[users.FirstName]
  private given Schema[users.LastName] = Schema.string[users.LastName]
  private given Schema[users.Email] = Schema.string[users.Email]
  given Schema[User] = Schema
    .derived[User]
    .jsonExample(
      User(
        Id("1CAF4C73F50D4514A041C9EDDB025A36"),
        Some(users.FirstName("Albert")),
        Some(users.LastName("Einstein")),
        Some(2.1)
      ): SearchEntity
    )

final case class Group(
    id: Id,
    name: Name,
    namespace: Namespace,
    description: Option[groups.Description] = None,
    score: Option[Double] = None
) extends SearchEntity

object Group:
  private given Schema[Id] = Schema.string[Id]
  private given Schema[Name] = Schema.string[Name]
  private given Schema[Namespace] = Schema.string[Namespace]
  private given Schema[groups.Description] = Schema.string[groups.Description]
  given Schema[Group] = Schema
    .derived[Group]
    .jsonExample(
      Group(
        Id("2CAF4C73F50D4514A041C9EDDB025A36"),
        Name("SDSC"),
        Namespace("SDSC"),
        Some(groups.Description("SDSC group")),
        Some(1.1)
      ): SearchEntity
    )

object SearchEntity:

  private val discriminatorField = "type"
  given AdtEncodingStrategy = AdtEncodingStrategy.flat(discriminatorField)
  given Codec[SearchEntity] = deriveAllCodecs[SearchEntity]

  given Schema[SearchEntity] = {
    val derived = Schema.derived[SearchEntity]
    derived.schemaType match {
      case s: SCoproduct[_] =>
        derived.copy(schemaType =
          s.addDiscriminatorField(
            FieldName(discriminatorField),
            Schema.string,
            List(
              summon[Schema[Project]].name.map(SRef(_)).map("Project" -> _),
              summon[Schema[User]].name.map(SRef(_)).map("User" -> _)
            ).flatten.toMap
          )
        )
      case s => derived
    }
  }
