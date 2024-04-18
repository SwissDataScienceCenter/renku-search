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

import io.renku.events.v1.{ProjectAuthorizationAdded, ProjectAuthorizationUpdated, ProjectMemberRole}
import io.renku.search.events.syntax.*
import io.renku.search.model.MemberRole
import io.renku.search.model.projects.*
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.solr.client.DocVersion

trait ProjectAuthorization:

  private def resolveRole(role: ProjectMemberRole, userId: String) =
    role.toModel match
      case MemberRole.Member => (Set.empty, Set(userId.toId))
      case MemberRole.Owner  => (Set(userId.toId), Set.empty)

  def fromProjectAuthorizationAdded(
      paa: ProjectAuthorizationAdded,
      version: DocVersion
  ): PartialEntityDocument.Project =
    val (owners, members) = resolveRole(paa.role, paa.userId)
    PartialEntityDocument.Project(
      id = paa.projectId.toId,
      version = version,
      owners = owners,
      members = members
    )

  def fromProjectAuthorizationUpdated(
      paa: ProjectAuthorizationUpdated,
      version: DocVersion
  ): PartialEntityDocument.Project =
    val (owners, members) = resolveRole(paa.role, paa.userId)
    PartialEntityDocument.Project(
      id = paa.projectId.toId,
      version = version,
      owners = owners,
      members = members
    )
