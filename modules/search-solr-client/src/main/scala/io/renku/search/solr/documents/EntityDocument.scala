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

import io.bullet.borer.*
import io.bullet.borer.derivation.MapBasedCodecs.*
import io.renku.search.model.*
import io.renku.search.model.projects.MemberRole
import io.renku.search.model.projects.MemberRole.{Member, Owner}
import io.renku.solr.client.EncoderSupport.*
import io.renku.search.model.projects.Visibility

sealed trait EntityDocument:
  val score: Option[Double]
  val id: Id
  def widen: EntityDocument = this

object EntityDocument:

  val allTypes: Set[String] = Set(Project.entityType, User.entityType)

  given AdtEncodingStrategy =
    AdtEncodingStrategy.flat(typeMemberName = discriminatorField)

  given Encoder[EntityDocument] = deriveAllEncoders[EntityDocument]
  given Decoder[EntityDocument] = deriveAllDecoders[EntityDocument]
  given Codec[EntityDocument] = Codec.of[EntityDocument]

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
) extends EntityDocument:

  def addMember(userId: Id, role: MemberRole): Project =
    role match {
      case Owner  => copy(owners = (userId :: owners).distinct, score = None)
      case Member => copy(members = (userId :: members).distinct, score = None)
    }

  def removeMember(userId: Id): Project =
    copy(
      owners = owners.filterNot(_ == userId),
      members = members.filterNot(_ == userId),
      score = None
    )

object Project:
  val entityType: String = "Project"

final case class User(
    id: Id,
    firstName: Option[users.FirstName] = None,
    lastName: Option[users.LastName] = None,
    name: Option[Name] = None,
    score: Option[Double] = None,
    visibility: Visibility = Visibility.Public
) extends EntityDocument

object User:
  val entityType: String = "User"

  def nameFrom(firstName: Option[String], lastName: Option[String]): Option[Name] =
    Option(List(firstName, lastName).flatten.mkString(" "))
      .filter(_.nonEmpty)
      .map(Name.apply)

  def of(
      id: Id,
      firstName: Option[users.FirstName] = None,
      lastName: Option[users.LastName] = None,
      score: Option[Double] = None
  ): User =
    User(
      id,
      firstName,
      lastName,
      nameFrom(firstName.map(_.value), lastName.map(_.value)),
      score
    )
