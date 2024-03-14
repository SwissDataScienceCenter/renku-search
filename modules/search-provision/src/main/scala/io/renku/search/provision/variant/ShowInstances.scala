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

package io.renku.search.provision.variant

import io.renku.events.v1.*
import cats.Show
import cats.syntax.all.*

trait ShowInstances:
  given Show[ProjectCreated] =
    Show.show[ProjectCreated](pc => show"slug '${pc.slug}'")

  given Show[ProjectUpdated] =
    Show.show[ProjectUpdated](pc => show"slug '${pc.slug}'")

  given Show[ProjectRemoved] =
    Show.show[ProjectRemoved](pr => show"slug '${pr.id}'")

  given Show[UserUpdated] =
    Show.show[UserUpdated](u => s"id '${u.id}'")

  given Show[UserAdded] =
    Show.show[UserAdded](u =>
      u.lastName.map(v => s"lastName '$v'").getOrElse(s"id '${u.id}'")
    )

  given Show[UserRemoved] =
    Show.show[UserRemoved](e => show"id '${e.id}'")

  given Show[ProjectAuthorizationAdded] =
    Show.show[ProjectAuthorizationAdded](v =>
      s"projectId '${v.projectId}', userId '${v.userId}', role '${v.role}'"
    )

  given Show[ProjectAuthorizationUpdated] =
    Show.show[ProjectAuthorizationUpdated](v =>
      s"projectId '${v.projectId}', userId '${v.userId}', role '${v.role}'"
    )

  given Show[ProjectAuthorizationRemoved] =
    Show.show[ProjectAuthorizationRemoved](v =>
      s"projectId '${v.projectId}', userId '${v.userId}'"
    )
