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
import io.renku.search.model.ModelGenerators.{idGen, memberRoleGen}
import io.renku.search.solr.client.SolrDocumentGenerators.projectDocumentGen
import munit.{FunSuite, ScalaCheckSuite}
import org.scalacheck.Prop

class ProjectSpec extends ScalaCheckSuite:

  test("addMember should add the given userId and role to the correct bucket in the doc"):
    Prop.forAll(projectDocumentGen, idGen, memberRoleGen) {
      case (project, userId, role) =>
        val updated = project.modifyEntityMembers(_.addMember(userId, role))
        role match {
          case Owner =>
            assertEquals(updated.owners, project.owners + userId)
          case Editor =>
            assertEquals(updated.editors, project.editors + userId)
          case Viewer =>
            assertEquals(updated.viewers, project.viewers + userId)
          case Member =>
            assertEquals(updated.members, project.members + userId)
        }
    }

  test(
    "addMember should add the given userId to the correct bucket and remove from the other buckets"
  ):
    Prop.forAll(projectDocumentGen, idGen) { case (project, userId) =>
      val updated = project
        .modifyEntityMembers(
          _.addMember(userId, Member)
            .addMember(userId, Owner)
        )

      assertEquals(updated.owners, project.owners + userId)
      assertEquals(updated.members, project.members - userId)
    }

  test("removeMember should remove the given userId from the correct bucket in the doc"):
    Prop.forAll(projectDocumentGen, idGen, memberRoleGen) {
      case (project, userId, role) =>
        val updated = project.modifyEntityMembers(_.addMember(userId, role))
        assertEquals(updated.modifyEntityMembers(_.removeMember(userId)), project)
    }

  test("removeMember should do nothing if there's no member/owner with the given userId"):
    Prop.forAll(projectDocumentGen, idGen, idGen, memberRoleGen) {
      case (project, existingUserId, toDeleteUserId, role) =>
        val updated = project.modifyEntityMembers(_.addMember(existingUserId, role))
        assertEquals(updated.modifyEntityMembers(_.removeMember(toDeleteUserId)), updated)
    }
