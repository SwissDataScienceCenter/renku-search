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

import io.bullet.borer.*
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs.{deriveAllCodecs, deriveCodec}
import io.renku.search.model.*

sealed trait SearchEntity:
  def id: Id
  def score: Option[Double]

object SearchEntity:
  private[api] val discriminatorField = "type"
  given AdtEncodingStrategy = AdtEncodingStrategy.flat(discriminatorField)
  given Codec[SearchEntity] = deriveAllCodecs[SearchEntity]

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

  final case class UserId(id: Id)
  object UserId:
    given Codec[UserId] = deriveCodec[UserId]

  final case class User(
      id: Id,
      namespace: Option[Namespace] = None,
      firstName: Option[FirstName] = None,
      lastName: Option[LastName] = None,
      score: Option[Double] = None
  ) extends SearchEntity

  final case class Group(
      id: Id,
      name: Name,
      namespace: Namespace,
      description: Option[Description] = None,
      score: Option[Double] = None
  ) extends SearchEntity
