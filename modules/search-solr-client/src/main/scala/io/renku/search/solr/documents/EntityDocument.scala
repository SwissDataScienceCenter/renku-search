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
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.{MapBasedCodecs, key}
import io.renku.json.EncoderSupport
import io.renku.search.model.*
import io.renku.search.model.MemberRole.*
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.DocVersion
import io.renku.solr.client.ResponseBody

sealed trait EntityDocument extends SolrDocument:
  val score: Option[Double]
  def widen: EntityDocument = this
  def setVersion(v: DocVersion): EntityDocument

object EntityDocument:
  given AdtEncodingStrategy =
    AdtEncodingStrategy.flat(typeMemberName = Fields.entityType.name)

  given Encoder[EntityDocument] = EncoderSupport.derive[EntityDocument]
  given Decoder[EntityDocument] = MapBasedCodecs.deriveAllDecoders[EntityDocument]
  given Codec[EntityDocument] = Codec.of[EntityDocument]

final case class Project(
    id: Id,
    @key("_version_") version: DocVersion = DocVersion.Off,
    name: Name,
    slug: Slug,
    repositories: Seq[Repository] = Seq.empty,
    visibility: Visibility,
    description: Option[Description] = None,
    createdBy: Id,
    creatorDetails: Option[ResponseBody[User]] = None,
    creationDate: CreationDate,
    owners: Set[Id] = Set.empty,
    editors: Set[Id] = Set.empty,
    viewers: Set[Id] = Set.empty,
    members: Set[Id] = Set.empty,
    groupOwners: Set[Id] = Set.empty,
    groupEditors: Set[Id] = Set.empty,
    groupViewers: Set[Id] = Set.empty,
    keywords: List[Keyword] = List.empty,
    namespace: Option[Namespace] = None,
    namespaceDetails: Option[ResponseBody[EntityDocument]] = None,
    score: Option[Double] = None
) extends EntityDocument:
  def setVersion(v: DocVersion): Project = copy(version = v)

  def toEntityMembers: EntityMembers =
    EntityMembers(owners, editors, viewers, members)

  def toGroupMembers: EntityMembers =
    EntityMembers(groupOwners, groupEditors, groupViewers, Set.empty)

  def setMembers(em: EntityMembers): Project =
    copy(
      owners = em.owners,
      editors = em.editors,
      viewers = em.viewers,
      members = em.members
    )

  def setGroupMembers(em: EntityMembers): Project =
    copy(groupOwners = em.owners, groupEditors = em.editors, groupViewers = em.viewers)

  def modifyEntityMembers(f: EntityMembers => EntityMembers): Project =
    setMembers(f(toEntityMembers))

  def modifyGroupMembers(f: EntityMembers => EntityMembers): Project =
    setGroupMembers(f(toGroupMembers))

object Project:
  given Encoder[Project] =
    EncoderSupport.deriveWith(
      DocumentKind.FullEntity.additionalField,
      EncoderSupport.AdditionalFields.productPrefix(Fields.entityType.name)
    )

final case class User(
    id: Id,
    @key("_version_") version: DocVersion = DocVersion.Off,
    firstName: Option[FirstName] = None,
    lastName: Option[LastName] = None,
    name: Option[Name] = None,
    namespace: Option[Namespace] = None,
    score: Option[Double] = None
) extends EntityDocument:
  def setVersion(v: DocVersion): User = copy(version = v)

object User:
  given Encoder[User] =
    EncoderSupport.deriveWith(
      DocumentKind.FullEntity.additionalField,
      EncoderSupport.AdditionalFields.productPrefix(Fields.entityType.name),
      EncoderSupport.AdditionalFields.const[User, String](
        Fields.visibility.name -> Visibility.Public.name
      )
    )

  def nameFrom(firstName: Option[String], lastName: Option[String]): Option[Name] =
    Option(List(firstName, lastName).flatten.mkString(" "))
      .filter(_.nonEmpty)
      .map(Name.apply)

  def of(
      id: Id,
      namespace: Option[Namespace] = None,
      firstName: Option[FirstName] = None,
      lastName: Option[LastName] = None,
      score: Option[Double] = None
  ): User =
    User(
      id,
      DocVersion.NotExists,
      firstName,
      lastName,
      nameFrom(firstName.map(_.value), lastName.map(_.value)),
      namespace,
      score
    )

final case class Group(
    id: Id,
    @key("_version_") version: DocVersion = DocVersion.Off,
    name: Name,
    namespace: Namespace,
    description: Option[Description] = None,
    owners: Set[Id] = Set.empty,
    editors: Set[Id] = Set.empty,
    viewers: Set[Id] = Set.empty,
    score: Option[Double] = None
) extends EntityDocument:
  def setVersion(v: DocVersion): Group = copy(version = v)

  def toEntityMembers: EntityMembers =
    EntityMembers(owners, editors, viewers, Set.empty)

  def setMembers(em: EntityMembers): Group =
    copy(
      owners = em.owners,
      editors = em.editors,
      viewers = em.viewers
    )

  def modifyEntityMembers(f: EntityMembers => EntityMembers): Group =
    setMembers(f(toEntityMembers))

object Group:
  given Encoder[Group] =
    EncoderSupport.deriveWith(
      DocumentKind.FullEntity.additionalField,
      EncoderSupport.AdditionalFields.productPrefix(Fields.entityType.name),
      EncoderSupport.AdditionalFields.const[Group, String](
        Fields.visibility.name -> Visibility.Public.name
      )
    )

  def of(
      id: Id,
      name: Name,
      namespace: Namespace,
      owners: Set[Id] = Set.empty,
      editors: Set[Id] = Set.empty,
      viewers: Set[Id] = Set.empty,
      description: Option[Description] = None,
      score: Option[Double] = None
  ): Group =
    Group(
      id,
      DocVersion.NotExists,
      name,
      namespace,
      description,
      owners,
      editors,
      viewers,
      score
    )
