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

package io.renku.search.provision.user

import io.github.arainko.ducktape.*
import io.renku.events.v1.UserAdded
import io.renku.events.v1.UserUpdated
import io.renku.search.solr.documents.User

trait UserSyntax:
  extension (added: UserAdded)
    def toSolrDocument: User = added
      .into[User]
      .transform(
        Field.default(_.score),
        Field.computed(_.name, u => User.nameFrom(u.firstName, u.lastName))
      )
    def update(updated: UserUpdated): UserAdded =
      added.copy(
        firstName = updated.firstName,
        lastName = updated.lastName,
        email = updated.email
      )
