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

import io.renku.events.v1.UserAdded
import io.renku.solr.client.DocVersion
import io.renku.search.events.syntax.*
import io.renku.search.solr.documents.User as UserDocument
import io.renku.events.v1.UserUpdated

trait Users:

  def fromUserAdded(ua: UserAdded, version: DocVersion): UserDocument =
    UserDocument(
      id = ua.id.toId,
      `_version_` = version,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName)
    )

  def fromUserUpdated(ua: UserUpdated, orig: UserDocument): UserDocument =
    orig.copy(
      id = ua.id.toId,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName),
      score = None
    )
