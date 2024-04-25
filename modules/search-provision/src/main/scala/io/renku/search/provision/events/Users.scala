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

package io.renku.search.provision.events

import cats.syntax.all.*

import io.renku.events.v1.UserUpdated
import io.renku.events.{v1, v2}
import io.renku.search.events.syntax.*
import io.renku.search.solr.documents.User as UserDocument
import io.renku.solr.client.DocVersion

trait Users:

  def fromUserAdded(ua: v1.UserAdded, version: DocVersion): UserDocument =
    UserDocument(
      id = ua.id.toId,
      version = version,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName),
      namespace = None
    )

  def fromUserAdded(ua: v2.UserAdded, version: DocVersion): UserDocument =
    UserDocument(
      id = ua.id.toId,
      version = version,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName),
      namespace = ua.namespace.toNamespace.some
    )

  def fromUserUpdated(ua: v1.UserUpdated, orig: UserDocument): UserDocument =
    orig.copy(
      id = ua.id.toId,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName),
      namespace = None,
      score = None
    )

  def fromUserUpdated(ua: v2.UserUpdated, orig: UserDocument): UserDocument =
    orig.copy(
      id = ua.id.toId,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName),
      namespace = ua.namespace.toNamespace.some,
      score = None
    )
