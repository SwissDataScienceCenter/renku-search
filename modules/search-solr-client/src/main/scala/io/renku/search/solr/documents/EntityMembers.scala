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

package io.renku.search.solr.documents

import io.renku.search.model.MemberRole.*
import io.renku.search.model.{Id, MemberRole}

final case class EntityMembers(
    owners: Set[Id] = Set.empty,
    editors: Set[Id] = Set.empty,
    viewers: Set[Id] = Set.empty,
    members: Set[Id] = Set.empty
):

  def addMember(userId: Id, role: MemberRole): EntityMembers =
    role match {
      case Owner =>
        copy(
          owners = owners + userId,
          editors = editors - userId,
          viewers = viewers - userId,
          members = members - userId
        )
      case Editor =>
        copy(
          owners = owners - userId,
          editors = editors + userId,
          viewers = viewers - userId,
          members = members - userId
        )
      case Viewer =>
        copy(
          owners = owners - userId,
          editors = editors - userId,
          viewers = viewers + userId,
          members = members - userId
        )
      case Member =>
        copy(
          owners = owners - userId,
          editors = editors - userId,
          viewers = viewers - userId,
          members = members + userId
        )
    }

  def addMembers(role: MemberRole, ids: List[Id]): EntityMembers =
    role match
      case Owner  => copy(owners = owners ++ ids)
      case Editor => copy(editors = editors ++ ids)
      case Viewer => copy(viewers = viewers ++ ids)
      case Member => copy(members = members ++ ids)

  def removeMember(userId: Id): EntityMembers =
    copy(
      owners = owners - userId,
      editors = editors - userId,
      viewers = viewers - userId,
      members = members - userId
    )
