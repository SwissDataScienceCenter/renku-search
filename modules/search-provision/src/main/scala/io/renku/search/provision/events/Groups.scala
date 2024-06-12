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

import io.renku.events.v2
import io.renku.events.v2.GroupMemberAdded
import io.renku.events.v2.GroupMemberUpdated
import io.renku.search.events.syntax.*
import io.renku.search.solr.documents.{Group as GroupDocument, PartialEntityDocument}
import io.renku.solr.client.DocVersion

trait Groups:

  def fromGroupAdded(ga: v2.GroupAdded, version: DocVersion): GroupDocument =
    GroupDocument(
      id = ga.id.toId,
      version = version,
      name = ga.name.toName,
      namespace = ga.namespace.toNamespace,
      description = ga.description.map(_.toDescription)
    )

  def fromGroupUpdated(
      ga: v2.GroupUpdated,
      version: DocVersion
  ): PartialEntityDocument.Group =
    PartialEntityDocument.Group(
      id = ga.id.toId,
      version = version,
      name = Some(ga.name.toName),
      namespace = Some(ga.namespace.toNamespace),
      description = ga.description.map(_.toDescription)
    )

  def fromGroupUpdated(gu: v2.GroupUpdated, orig: GroupDocument): GroupDocument =
    orig.copy(
      id = gu.id.toId,
      name = gu.name.toName,
      namespace = gu.namespace.toNamespace,
      description = gu.description.map(_.toDescription)
    )

  def fromGroupUpdated(
      gu: v2.GroupUpdated,
      orig: PartialEntityDocument.Group
  ): PartialEntityDocument.Group =
    orig.copy(
      id = gu.id.toId,
      name = Some(gu.name.toName),
      namespace = Some(gu.namespace.toNamespace),
      description = gu.description.map(_.toDescription)
    )

  def fromGroupMemberAdded(
      ga: GroupMemberAdded,
      version: DocVersion
  ): PartialEntityDocument.Group =
    PartialEntityDocument
      .Group(
        id = ga.groupId.toId,
        version = version
      )
      .modifyEntityMembers(_.addMember(ga.userId.toId, ga.role.toModel))

  def fromGroupMemberUpdated(
      ga: GroupMemberUpdated,
      version: DocVersion
  ): PartialEntityDocument.Group =
    PartialEntityDocument
      .Group(
        id = ga.groupId.toId,
        version = version
      )
      .modifyEntityMembers(_.addMember(ga.userId.toId, ga.role.toModel))
