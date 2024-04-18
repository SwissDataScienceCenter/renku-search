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

package io.renku.search.provision.handler

import io.renku.events.{v1, v2}
import cats.Show
import cats.syntax.all.*

trait ShowInstances:
  given v1projectCreatedShow: Show[v1.ProjectCreated] =
    Show.show[v1.ProjectCreated](pc => show"slug '${pc.slug}'")

  given v2projectCreatedShow: Show[v2.ProjectCreated] =
    Show.show[v2.ProjectCreated](pc => show"slug '${pc.slug}'")

  given Show[v1.ProjectUpdated] =
    Show.show[v1.ProjectUpdated](pc => show"slug '${pc.slug}'")

  given Show[v1.ProjectRemoved] =
    Show.show[v1.ProjectRemoved](pr => show"slug '${pr.id}'")

  given Show[v1.UserUpdated] =
    Show.show[v1.UserUpdated](u => s"id '${u.id}'")

  given Show[v1.UserAdded] =
    Show.show[v1.UserAdded](u =>
      u.lastName.map(v => s"lastName '$v'").getOrElse(s"id '${u.id}'")
    )

  given Show[v1.UserRemoved] =
    Show.show[v1.UserRemoved](e => show"id '${e.id}'")

  given Show[v1.ProjectAuthorizationAdded] =
    Show.show[v1.ProjectAuthorizationAdded](v =>
      s"projectId '${v.projectId}', userId '${v.userId}', role '${v.role}'"
    )

  given Show[v1.ProjectAuthorizationUpdated] =
    Show.show[v1.ProjectAuthorizationUpdated](v =>
      s"projectId '${v.projectId}', userId '${v.userId}', role '${v.role}'"
    )

  given Show[v1.ProjectAuthorizationRemoved] =
    Show.show[v1.ProjectAuthorizationRemoved](v =>
      s"projectId '${v.projectId}', userId '${v.userId}'"
    )

object ShowInstances extends ShowInstances
