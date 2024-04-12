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

import io.renku.events.v1.{Visibility as EventVisibility, *}
import io.renku.search.model.{Id, Name}
import io.renku.search.model.projects.*
import java.time.Instant
import io.renku.search.model.users.FirstName
import io.renku.search.model.users.LastName

trait syntax:
  extension (self: EventVisibility)
    def toModel: Visibility =
      Visibility.unsafeFromString(self.name())

  extension (self: ProjectMemberRole)
    def toModel: MemberRole =
      MemberRole.unsafeFromString(self.name())

  extension (self: String)
    def toId: Id = Id(self)
    def toName: Name = Name(self)
    def toSlug: Slug = Slug(self)
    def toRepository: Repository = Repository(self)
    def toDescription: Description = Description(self)
    def toFirstName: FirstName = FirstName(self)
    def toLastName: LastName = LastName(self)

  extension (self: Instant) def toCreationDate: CreationDate = CreationDate(self)

object syntax extends syntax
