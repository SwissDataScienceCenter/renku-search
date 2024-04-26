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
import cats.kernel.Monoid

/** Set of properties representing user-role relationship for an entity. Each property
  * corresponds to a [[MemberRole]]. A user id can only be in one such set, since roles
  * are ordered and a "higher" role implies a "lower" role.
  */
final case class EntityMembers(
    owners: Set[Id] = Set.empty,
    editors: Set[Id] = Set.empty,
    viewers: Set[Id] = Set.empty,
    members: Set[Id] = Set.empty
):
  def getMemberIds(role: MemberRole): Set[Id] =
    role match
      case Owner  => owners
      case Editor => editors
      case Viewer => viewers
      case Member => members

  def addMember(userId: Id, role: MemberRole): EntityMembers =
    addMembers(role, List(userId))

  def addMembers(role: MemberRole, ids: Iterable[Id]): EntityMembers =
    role match
      case Owner =>
        copy(
          owners = owners ++ ids,
          editors = editors -- ids,
          viewers = viewers -- ids,
          members = members -- ids
        )
      case Editor =>
        copy(
          owners = owners -- ids,
          editors = editors ++ ids,
          viewers = viewers -- ids,
          members = members -- ids
        )
      case Viewer =>
        copy(
          owners = owners -- ids,
          editors = editors -- ids,
          viewers = viewers ++ ids,
          members = members -- ids
        )
      case Member =>
        copy(
          owners = owners -- ids,
          editors = editors -- ids,
          viewers = viewers -- ids,
          members = members ++ ids
        )

  def removeMember(userId: Id): EntityMembers =
    copy(
      owners = owners - userId,
      editors = editors - userId,
      viewers = viewers - userId,
      members = members - userId
    )

  def ++(other: EntityMembers): EntityMembers =
    MemberRole.valuesLowerFirst.foldLeft(this) { (acc, role) =>
      acc.addMembers(role, getMemberIds(role))
    }

object EntityMembers:
  val empty: EntityMembers = EntityMembers(Set.empty, Set.empty, Set.empty, Set.empty)

  def apply(id: Id, role: MemberRole): EntityMembers =
    empty.addMember(id, role)

  given Monoid[EntityMembers] =
    Monoid.instance(empty, _ ++ _)
