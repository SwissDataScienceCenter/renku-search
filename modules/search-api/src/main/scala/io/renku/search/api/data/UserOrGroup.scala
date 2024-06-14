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

import scala.util.control.NoStackTrace

import cats.data.NonEmptyList
import cats.syntax.all.*

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.renku.search.api.data.UserOrGroup.InvalidValue
import io.renku.search.model.*
import io.renku.search.solr.documents.{Group as GroupDocument, User as UserDocument}
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.schema.FieldName

final case class UserOrGroup(
    id: Id,
    @key("type") entityType: EntityType,
    name: Option[Name] = None,
    namespace: Option[Namespace] = None,
    firstName: Option[FirstName] = None,
    lastName: Option[LastName] = None,
    description: Option[Description] = None
):
  def fold[A](
      fu: SearchEntity.User => A,
      fg: SearchEntity.Group => A
  ): Either[InvalidValue, A] =
    entityType match
      case EntityType.User =>
        Right(fu(SearchEntity.User(id, namespace, firstName, lastName, None)))
      case EntityType.Group =>
        val nameValid = name.toValidNel(Fields.name)
        val nsValid = namespace.toValidNel(Fields.namespace)

        (nameValid, nsValid)
          .mapN { (n, ns) =>
            val g = SearchEntity.Group(id, n, ns, description, None)
            fg(g)
          }
          .toEither
          .leftMap(InvalidValue.MissingFields(_, this))

      case EntityType.Project => Left(InvalidValue.InvalidEntityType(this))

object UserOrGroup:
  given Encoder[UserOrGroup] = MapBasedCodecs.deriveEncoder
  given Decoder[UserOrGroup] = MapBasedCodecs.deriveDecoder

  def apply(u: SearchEntity.User): UserOrGroup =
    UserOrGroup(u.id, EntityType.User, None, u.namespace, u.firstName, u.lastName, None)

  def apply(g: SearchEntity.Group): UserOrGroup =
    UserOrGroup(
      g.id,
      EntityType.Group,
      g.name.some,
      g.namespace.some,
      None,
      None,
      g.description
    )

  def apply(doc: UserDocument): UserOrGroup =
    UserOrGroup(
      doc.id,
      EntityType.User,
      doc.name,
      doc.namespace,
      doc.firstName,
      doc.lastName,
      None
    )

  def apply(doc: GroupDocument): UserOrGroup =
    UserOrGroup(
      doc.id,
      EntityType.Group,
      doc.name.some,
      doc.namespace.some,
      None,
      None,
      doc.description
    )

  sealed trait InvalidValue
  object InvalidValue:
    final case class InvalidEntityType(value: UserOrGroup)
        extends RuntimeException(
          s"Expected user or group entity, but got: ${value.entityType} in $value"
        )
        with NoStackTrace
        with InvalidValue

    final case class MissingFields(fields: NonEmptyList[FieldName], value: UserOrGroup)
        extends RuntimeException(s"Missing fields $fields for $value")
        with NoStackTrace
        with InvalidValue

  end InvalidValue
