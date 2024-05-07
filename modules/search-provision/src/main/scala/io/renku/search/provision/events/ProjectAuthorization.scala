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

import io.renku.events.v1.{ProjectAuthorizationAdded, ProjectAuthorizationUpdated}
import io.renku.events.v2.ProjectMemberAdded
import io.renku.events.v2.ProjectMemberUpdated
import io.renku.events.{v1, v2}
import io.renku.search.events.syntax.*
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.solr.client.DocVersion

trait ProjectAuthorization:

  def fromProjectAuthorizationAdded(
      paa: ProjectAuthorizationAdded,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .modifyEntityMembers(_.addMember(paa.userId.toId, paa.role.toModel))

  def fromProjectMemberAdded(
      paa: ProjectMemberAdded,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .modifyEntityMembers(_.addMember(paa.userId.toId, paa.role.toModel))

  def fromProjectAuthorizationUpdated(
      paa: ProjectAuthorizationUpdated,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .modifyEntityMembers(_.addMember(paa.userId.toId, paa.role.toModel))

  def fromProjectMemberUpdated(
      paa: ProjectMemberUpdated,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .modifyEntityMembers(_.addMember(paa.userId.toId, paa.role.toModel))
