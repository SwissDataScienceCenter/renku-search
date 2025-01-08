package io.renku.search.solr.documents

import cats.kernel.Monoid

import io.renku.search.model.MemberRole.*
import io.renku.search.model.{Id, MemberRole}

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
    removeMembers(Seq(userId))

  def removeMembers(userIds: Seq[Id]): EntityMembers =
    copy(
      owners = owners -- userIds,
      editors = editors -- userIds,
      viewers = viewers -- userIds,
      members = members -- userIds
    )

  def contains(id: Id): Boolean =
    owners.contains(id) || editors.contains(id) ||
      viewers.contains(id) || members.contains(id)

  def isEmpty: Boolean =
    MemberRole.values.forall(getMemberIds(_).isEmpty)

  def nonEmpty: Boolean = !isEmpty

  def allIds: Set[Id] = MemberRole.values.flatMap(getMemberIds).toSet

  def ++(other: EntityMembers): EntityMembers =
    MemberRole.valuesLowerFirst.foldLeft(this) { (acc, role) =>
      acc.addMembers(role, other.getMemberIds(role))
    }

  override def toString =
    s"EntityMembers(owners=$owners, editors=$editors, viewers=$viewers, members=$members)"

object EntityMembers:
  val empty: EntityMembers = EntityMembers(Set.empty, Set.empty, Set.empty, Set.empty)

  def apply(id: Id, role: MemberRole): EntityMembers =
    empty.addMember(id, role)

  given Monoid[EntityMembers] =
    Monoid.instance(empty, _ ++ _)
