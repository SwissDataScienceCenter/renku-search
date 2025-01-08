package io.renku.search.solr.documents

import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.model.MemberRole
import io.renku.search.model.ModelGenerators
import munit.*

class EntityMembersSpec extends FunSuite:
  val someId: Id = ModelGenerators.idGen.generateOne

  MemberRole.values.foreach { role =>
    test(s"addMember to empty set: $role")
    val em = EntityMembers.empty.addMember(someId, role)
    assertEquals(em.getMemberIds(role), Set(someId))
    MemberRole.values.filter(_ != role).foreach { nr =>
      assertEquals(em.getMemberIds(nr), Set.empty)
    }
  }

  MemberRole.values.foreach { role =>
    test(s"concatenate empty with $role"):
      val em1 = EntityMembers.empty ++ EntityMembers.empty.addMember(someId, role)
      val em2 = EntityMembers.empty.addMember(someId, role) ++ EntityMembers.empty
      assertEquals(em1.getMemberIds(role), Set(someId))
      assertEquals(em2.getMemberIds(role), Set(someId))
      MemberRole.values.filter(_ != role).foreach { nr =>
        assertEquals(em1.getMemberIds(nr), Set.empty)
        assertEquals(em2.getMemberIds(nr), Set.empty)
      }
  }

  test(
    "addMember should add the userId to the relevant bucket and remove from the other bucket if existed"
  ):
    val userId = Id("42")
    MemberRole.valuesV2.foreach { role =>
      val existing = EntityMembers(
        owners = Set(userId),
        editors = Set(userId),
        viewers = Set(userId),
        members = Set(userId)
      )

      val updated = existing.addMember(userId, role)

      role match {
        case MemberRole.Owner =>
          assertEquals(updated.owners, existing.owners)
          assertEquals(updated.editors, existing.editors - userId)
          assertEquals(updated.viewers, existing.viewers - userId)
          assertEquals(updated.members, existing.members - userId)
        case MemberRole.Editor =>
          assertEquals(updated.owners, existing.owners - userId)
          assertEquals(updated.editors, existing.editors)
          assertEquals(updated.viewers, existing.viewers - userId)
          assertEquals(updated.members, existing.members - userId)
        case MemberRole.Viewer =>
          assertEquals(updated.owners, existing.owners - userId)
          assertEquals(updated.editors, existing.editors - userId)
          assertEquals(updated.viewers, existing.viewers)
          assertEquals(updated.members, existing.members - userId)
        case MemberRole.Member =>
          assertEquals(updated.owners, existing.owners - userId)
          assertEquals(updated.editors, existing.editors - userId)
          assertEquals(updated.viewers, existing.viewers - userId)
          assertEquals(updated.members, existing.members)
      }
    }
