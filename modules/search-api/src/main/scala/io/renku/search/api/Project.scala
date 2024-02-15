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

package io.renku.search.api

import io.bullet.borer.derivation.MapBasedCodecs.{deriveDecoder, deriveEncoder}
import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.model.*
import sttp.tapir.Schema
import sttp.tapir.SchemaType.SDateTime

final case class Project(
    id: projects.Id,
    name: projects.Name,
    slug: projects.Slug,
    repositories: Seq[projects.Repository],
    visibility: projects.Visibility,
    description: Option[projects.Description] = None,
    createdBy: User,
    creationDate: projects.CreationDate,
    members: Seq[User]
)

final case class User(
    id: users.Id
)

object Project:
  given Encoder[User] = deriveEncoder
  given Decoder[User] = deriveDecoder
  given Encoder[Project] = deriveEncoder
  given Decoder[Project] = deriveDecoder

  given Schema[User] = {
    given Schema[users.Id] = Schema.string[users.Id]
    Schema.derived[User]
  }
  given Schema[Project] = {
    given Schema[projects.Id] = Schema.string[projects.Id]
    given Schema[projects.Name] = Schema.string[projects.Name]
    given Schema[projects.Slug] = Schema.string[projects.Slug]
    given Schema[projects.Repository] = Schema.string[projects.Repository]
    given Schema[projects.Visibility] =
      Schema.derivedEnumeration[projects.Visibility].defaultStringBased
    given Schema[projects.Description] = Schema.string[projects.Description]
    given Schema[projects.CreationDate] = Schema(SDateTime())

    Schema.derived[Project]
  }
