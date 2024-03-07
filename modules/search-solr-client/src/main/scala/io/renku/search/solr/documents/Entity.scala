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

package io.renku.search.solr.documents

import io.bullet.borer.derivation.MapBasedCodecs.*
import io.bullet.borer.{AdtEncodingStrategy, Decoder, Encoder}
import io.renku.search.model.{projects, users}
import io.renku.solr.client.EncoderSupport.*

sealed trait Entity:
  val score: Option[Double]

object Entity:

  val allTypes: Set[String] = Set(Project.entityType, User.entityType)

  given AdtEncodingStrategy =
    AdtEncodingStrategy.flat(typeMemberName = discriminatorField)

  given Encoder[Entity] = deriveEncoder[Entity]
  given Decoder[Entity] = deriveAllDecoders[Entity]

final case class Project(
    id: projects.Id,
    name: projects.Name,
    slug: projects.Slug,
    repositories: Seq[projects.Repository] = Seq.empty,
    visibility: projects.Visibility,
    description: Option[projects.Description] = None,
    createdBy: users.Id,
    creationDate: projects.CreationDate,
    score: Option[Double] = None
) extends Entity

object Project:
  val entityType: String = "Project"
  given Encoder[Project] = deriveWithDiscriminator

final case class User(
    id: users.Id,
    firstName: Option[users.FirstName] = None,
    lastName: Option[users.LastName] = None,
    email: Option[users.Email] = None,
    score: Option[Double] = None
) extends Entity

object User:
  val entityType: String = "User"
  given Encoder[User] = deriveWithDiscriminator