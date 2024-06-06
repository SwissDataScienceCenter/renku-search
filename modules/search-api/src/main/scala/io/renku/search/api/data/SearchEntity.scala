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

import io.bullet.borer.*
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs.{deriveAllCodecs, deriveCodec}
import io.renku.search.api.tapir.SchemaSyntax.*
import io.renku.search.model.*
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.*
import sttp.tapir.generic.Configuration
import sttp.tapir.{FieldName, Schema, SchemaType}

sealed trait SearchEntity

final case class Project(
    id: Id,
    name: Name,
    slug: Slug,
    namespace: Option[Namespace],
    repositories: Seq[Repository],
    visibility: Visibility,
    description: Option[Description] = None,
    createdBy: UserId,
    creationDate: CreationDate,
    keywords: List[Keyword] = Nil,
    score: Option[Double] = None
) extends SearchEntity

object Project:
  private given Schema[Id] = Schema.string[Id]
  private given Schema[Name] = Schema.string[Name]
  private given Schema[Namespace] = Schema.string[Namespace]
  private given Schema[Slug] = Schema.string[Slug]
  private given Schema[Repository] = Schema.string[Repository]
  private given Schema[Visibility] =
    Schema.derivedEnumeration[Visibility].defaultStringBased
  private given Schema[Description] = Schema.string[Description]
  private given Schema[CreationDate] = Schema(SDateTime())
  private given Schema[Keyword] = Schema.string[Keyword]
  given Schema[Project] = Schema
    .derived[Project]
    .jsonExample(
      Project(
        Id("01HRA7AZ2Q234CDQWGA052F8MK"),
        Name("renku"),
        Slug("renku"),
        Some(Namespace("renku/renku")),
        Seq(Repository("https://github.com/renku")),
        Visibility.Public,
        Some(Description("Renku project")),
        UserId(Id("1CAF4C73F50D4514A041C9EDDB025A36")),
        CreationDate(Instant.now),
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
    namespace: Option[Namespace] = None,
    firstName: Option[FirstName] = None,
    lastName: Option[LastName] = None,
    score: Option[Double] = None
) extends SearchEntity

object User:
  private given Schema[Id] = Schema.string[Id]
  private given Schema[FirstName] = Schema.string[FirstName]
  private given Schema[LastName] = Schema.string[LastName]
  private given Schema[Email] = Schema.string[Email]
  private given Schema[Namespace] = Schema.string[Namespace]
  given Schema[User] = Schema
    .derived[User]
    .jsonExample(
      User(
        Id("1CAF4C73F50D4514A041C9EDDB025A36"),
        Some(Namespace("renku/renku")),
        Some(FirstName("Albert")),
        Some(LastName("Einstein")),
        Some(2.1)
      ): SearchEntity
    )

final case class Group(
    id: Id,
    name: Name,
    namespace: Namespace,
    description: Option[Description] = None,
    score: Option[Double] = None
) extends SearchEntity

object Group:
  private given Schema[Id] = Schema.string[Id]
  private given Schema[Name] = Schema.string[Name]
  private given Schema[Namespace] = Schema.string[Namespace]
  private given Schema[Description] = Schema.string[Description]
  given Schema[Group] = Schema
    .derived[Group]
    .jsonExample(
      Group(
        Id("2CAF4C73F50D4514A041C9EDDB025A36"),
        Name("SDSC"),
        Namespace("SDSC"),
        Some(Description("SDSC group")),
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
