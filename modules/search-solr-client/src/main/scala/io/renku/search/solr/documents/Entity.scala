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

import io.bullet.borer._
import io.bullet.borer.derivation.MapBasedCodecs.*
import io.renku.search.model._
import io.renku.search.model.projects.MemberRole
import io.renku.search.model.projects.MemberRole.{Member, Owner}
import io.renku.solr.client.EncoderSupport.*

opaque type DocumentId = String
object DocumentId:
  def apply(v: String): DocumentId = v
  extension (self: DocumentId) def name: String = self

sealed trait Entity:
  val score: Option[Double]
  val id: Id
  def widen: Entity = this

object Entity:

  val allTypes: Set[String] = Set(Project.entityType, User.entityType)

  given AdtEncodingStrategy =
    AdtEncodingStrategy.flat(typeMemberName = discriminatorField)

  given Encoder[Entity] = deriveAllEncoders[Entity]
  given Decoder[Entity] = deriveAllDecoders[Entity]
  given Codec[Entity] = Codec.of[Entity]

final case class Project(
    id: Id,
    name: Name,
    slug: projects.Slug,
    repositories: Seq[projects.Repository] = Seq.empty,
    visibility: projects.Visibility,
    description: Option[projects.Description] = None,
    createdBy: Id,
    creationDate: projects.CreationDate,
    owners: List[Id] = List.empty,
    members: List[Id] = List.empty,
    score: Option[Double] = None
) extends Entity:

  def addMember(userId: Id, role: MemberRole): Project =
    role match {
      case Owner  => copy(owners = (userId :: owners).distinct)
      case Member => copy(members = (userId :: members).distinct)
    }

  def removeMember(userId: Id): Project =
    copy(owners = owners.filterNot(_ == userId), members = members.filterNot(_ == userId))

object Project:
  val entityType: String = "project"

final case class User(
    id: Id,
    firstName: Option[users.FirstName] = None,
    lastName: Option[users.LastName] = None,
    name: Option[Name] = None,
    email: Option[users.Email] = None,
    score: Option[Double] = None
) extends Entity

object User:
  val entityType: String = "user"

  def nameFrom(firstName: Option[String], lastName: Option[String]): Option[Name] =
    Option(List(firstName, lastName).flatten.mkString(" "))
      .filter(_.nonEmpty)
      .map(Name.apply)

  def of(
      id: Id,
      firstName: Option[users.FirstName] = None,
      lastName: Option[users.LastName] = None,
      email: Option[users.Email] = None,
      score: Option[Double] = None
  ): User =
    User(
      id,
      firstName,
      lastName,
      nameFrom(firstName.map(_.value), lastName.map(_.value)),
      email,
      score
    )
