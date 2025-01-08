package io.renku.search.solr.documents

import io.renku.search.GeneratorSyntax
import io.renku.search.model.MemberRole.*
import io.renku.search.model.ModelGenerators.{idGen, memberRoleGen}
import io.renku.search.solr.client.SolrDocumentGenerators.partialProjectGen
import io.renku.search.solr.documents.PartialEntityDocument.Project
import munit.{FunSuite, ScalaCheckSuite}
import org.scalacheck.Prop

class PartialProjectSpec extends ScalaCheckSuite with GeneratorSyntax:

  test("add should add the userId to the relevant bucket"):
    Prop.forAll(partialProjectGen, idGen, memberRoleGen) {
      case (existing, userId, role) =>
        val updated = existing.modifyEntityMembers(_.addMember(userId, role))

        role match {
          case Owner =>
            assertEquals(updated.owners, existing.owners + userId)
            assertEquals(updated.editors, existing.editors)
            assertEquals(updated.viewers, existing.viewers)
            assertEquals(updated.members, existing.members)
          case Editor =>
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.editors, existing.editors + userId)
            assertEquals(updated.viewers, existing.viewers)
            assertEquals(updated.members, existing.members)
          case Viewer =>
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.editors, existing.editors)
            assertEquals(updated.viewers, existing.viewers + userId)
            assertEquals(updated.members, existing.members)
          case Member =>
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.editors, existing.editors)
            assertEquals(updated.viewers, existing.viewers)
            assertEquals(updated.members, existing.members + userId)
        }
    }

  test("removeMember should remove the given userId from the correct bucket in the doc"):
    Prop.forAll(partialProjectGen, idGen, memberRoleGen) { case (project, userId, role) =>
      val updated = project.modifyEntityMembers(_.addMember(userId, role))
      assertEquals(updated.modifyEntityMembers(_.removeMember(userId)), project)
    }

  test("removeMember should do nothing if there's no member/owner with the given userId"):
    Prop.forAll(partialProjectGen, idGen, idGen, memberRoleGen) {
      case (project, existingUserId, toDeleteUserId, role) =>
        val updated = project.modifyEntityMembers(_.addMember(existingUserId, role))
        assertEquals(updated.modifyEntityMembers(_.removeMember(toDeleteUserId)), updated)
    }
