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

import io.bullet.borer.derivation.MapBasedCodecs.{deriveAllCodecs, deriveCodec}
import io.bullet.borer.{AdtEncodingStrategy, Codec, Decoder, Encoder}
import io.bullet.borer.NullOptions.given
import io.renku.search.model.*
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.{SDateTime, SProductField}
import sttp.tapir.generic.Configuration
import sttp.tapir.{FieldName, Schema, SchemaType}

sealed trait SearchEntity

final case class Project(
    id: projects.Id,
    name: projects.Name,
    slug: projects.Slug,
    repositories: Seq[projects.Repository],
    visibility: projects.Visibility,
    description: Option[projects.Description] = None,
    createdBy: User,
    creationDate: projects.CreationDate,
    members: Seq[User],
    score: Option[Double] = None
) extends SearchEntity

object Project:
  private given Schema[projects.Id] = Schema.string[projects.Id]
  private given Schema[projects.Name] = Schema.string[projects.Name]
  private given Schema[projects.Slug] = Schema.string[projects.Slug]
  private given Schema[projects.Repository] = Schema.string[projects.Repository]
  private given Schema[projects.Visibility] =
    Schema.derivedEnumeration[projects.Visibility].defaultStringBased
  private given Schema[projects.Description] = Schema.string[projects.Description]
  private given Schema[projects.CreationDate] = Schema(SDateTime())
  given Schema[Project] = Schema.derived[Project]

final case class User(
    id: users.Id
)

object User:
  given Codec[User] = deriveCodec[User]

  private given Schema[users.Id] = Schema.string[users.Id]
  given Schema[User] = Schema.derived[User]

object SearchEntity:

  private val discriminatorField = "type"
  given AdtEncodingStrategy = AdtEncodingStrategy.flat(discriminatorField)
  given Codec[SearchEntity] = deriveAllCodecs[SearchEntity]

  given Schema[SearchEntity] = {
    val derived = Schema.derived[SearchEntity]
    derived.schemaType match {
      case s: SchemaType.SCoproduct[_] =>
        derived.copy(schemaType =
          s.addDiscriminatorField(
            FieldName(discriminatorField),
            Schema.string,
            List(
              implicitly[Schema[Project]].name.map(SchemaType.SRef(_)).map("Project" -> _)
            ).flatten.toMap
          )
        )
      case s => derived
    }
  }
