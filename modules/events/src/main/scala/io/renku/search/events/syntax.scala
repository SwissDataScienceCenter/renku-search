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

package io.renku.search.events

import java.time.Instant

import io.renku.events.{v1, v2}
import io.renku.search.model.*

trait syntax:
  extension (self: v1.Visibility)
    def toModel: Visibility =
      Visibility.unsafeFromString(self.name())

  extension (self: v2.Visibility)
    def toModel: Visibility =
      Visibility.unsafeFromString(self.name())

  extension (self: v1.ProjectMemberRole)
    def toModel: MemberRole =
      MemberRole.unsafeFromString(self.name())

  extension (self: v2.MemberRole)
    def toModel: MemberRole =
      MemberRole.unsafeFromString(self.name())

  extension (self: String)
    def toId: Id = Id(self)
    def toName: Name = Name(self)
    def toNamespace: Namespace = Namespace(self)
    def toSlug: Slug = Slug(self)
    def toRepository: Repository = Repository(self)
    def toDescription: Description = Description(self)
    def toFirstName: FirstName = FirstName(self)
    def toLastName: LastName = LastName(self)
    def toKeyword: Keyword = Keyword(self)

  extension (self: Instant) def toCreationDate: CreationDate = CreationDate(self)

object syntax extends syntax
