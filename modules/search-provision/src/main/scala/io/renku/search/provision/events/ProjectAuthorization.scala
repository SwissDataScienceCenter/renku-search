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

import io.renku.events.{v1, v2}
import io.renku.events.v1.{ProjectAuthorizationAdded, ProjectAuthorizationUpdated}
import io.renku.search.events.syntax.*
import io.renku.search.solr.documents.{EntityMembers, PartialEntityDocument}
import io.renku.solr.client.DocVersion
import io.renku.events.v2.ProjectMemberAdded

trait ProjectAuthorization:

  private def resolveRole(role: v1.ProjectMemberRole, userId: String) =
    role match
      case v1.ProjectMemberRole.MEMBER => (Set.empty, Set(userId.toId))
      case v1.ProjectMemberRole.OWNER  => (Set(userId.toId), Set.empty)

  private def resolveRole(role: v2.MemberRole, userId: String) =
    role match
      case v2.MemberRole.OWNER  => (Set(userId.toId), Set.empty, Set.empty)
      case v2.MemberRole.EDITOR => (Set.empty, Set(userId.toId), Set.empty)
      case v2.MemberRole.VIEWER => (Set.empty, Set.empty, Set(userId.toId))

  def fromProjectAuthorizationAdded(
      paa: ProjectAuthorizationAdded,
      version: DocVersion
  ): PartialEntityDocument.Project =
    val (owners, members) = resolveRole(paa.role, paa.userId)
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .apply(
        EntityMembers(
          owners = owners,
          editors = Set.empty,
          viewers = Set.empty,
          members = members
        )
      )

  def fromProjectMemberAdded(
      paa: ProjectMemberAdded,
      version: DocVersion
  ): PartialEntityDocument.Project =
    val (owners, editors, viewers) = resolveRole(paa.role, paa.userId)
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .apply(
        EntityMembers(
          owners = owners,
          editors = editors,
          viewers = viewers,
          members = Set.empty
        )
      )

  def fromProjectAuthorizationUpdated(
      paa: ProjectAuthorizationUpdated,
      version: DocVersion
  ): PartialEntityDocument.Project =
    val (owners, members) = resolveRole(paa.role, paa.userId)
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .apply(
        EntityMembers(
          owners = owners,
          editors = Set.empty,
          viewers = Set.empty,
          members = members
        )
      )
