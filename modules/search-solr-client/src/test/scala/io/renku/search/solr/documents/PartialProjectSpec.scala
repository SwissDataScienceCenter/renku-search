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

import io.renku.search.GeneratorSyntax
import io.renku.search.model.MemberRole.*
import io.renku.search.model.ModelGenerators.{idGen, memberRoleGen}
import io.renku.search.solr.client.SolrDocumentGenerators.partialProjectGen
import io.renku.search.solr.documents.PartialEntityDocument.Project
import io.renku.solr.client.DocVersion
import io.renku.solr.client.SolrClientGenerator.versionGen
import munit.{FunSuite, ScalaCheckSuite}
import org.scalacheck.Prop

class PartialProjectSpec extends ScalaCheckSuite with GeneratorSyntax:

  test("add should add the userId to the relevant bucket"):
    Prop.forAll(partialProjectGen, idGen, memberRoleGen) {
      case (existing, userId, role) =>
        val updated = existing.addMember(userId, role)

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

  test(
    "add should add the userId to the relevant bucket and remove from the other bucket if existed"
  ):
    Prop.forAll(idGen, versionGen, idGen, memberRoleGen) {
      case (projectId, version, userId, role) =>
        val existing = Project(id = projectId, version = version)
          .apply(
            EntityMembers(
              owners = Set(userId),
              editors = Set(userId),
              viewers = Set(userId),
              members = Set(userId)
            )
          )

        val updated = existing.addMember(userId, role)

        role match {
          case Owner =>
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.editors, existing.editors - userId)
            assertEquals(updated.viewers, existing.viewers - userId)
            assertEquals(updated.members, existing.members - userId)
          case Editor =>
            assertEquals(updated.owners, existing.owners - userId)
            assertEquals(updated.editors, existing.editors)
            assertEquals(updated.viewers, existing.viewers - userId)
            assertEquals(updated.members, existing.members - userId)
          case Viewer =>
            assertEquals(updated.owners, existing.owners - userId)
            assertEquals(updated.editors, existing.editors - userId)
            assertEquals(updated.viewers, existing.viewers)
            assertEquals(updated.members, existing.members - userId)
          case Member =>
            assertEquals(updated.owners, existing.owners - userId)
            assertEquals(updated.editors, existing.editors - userId)
            assertEquals(updated.viewers, existing.viewers - userId)
            assertEquals(updated.members, existing.members)
        }
    }

  test("applyTo should add members and owners from the caller object"):
    Prop.forAll(partialProjectGen, idGen, memberRoleGen) {
      case (existing, userId, role) =>
        val update = Project(existing.id, existing.version).addMember(userId, role)

        val updated = existing.applyTo(update).asInstanceOf[Project]

        role match {
          case Owner =>
            assertEquals(updated.version, existing.version)
            assertEquals(updated.owners, existing.owners + userId)
            assertEquals(updated.editors, existing.editors)
            assertEquals(updated.viewers, existing.viewers)
            assertEquals(updated.members, existing.members)
          case Editor =>
            assertEquals(updated.version, existing.version)
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.editors, existing.editors + userId)
            assertEquals(updated.viewers, existing.viewers)
            assertEquals(updated.members, existing.members)
          case Viewer =>
            assertEquals(updated.version, existing.version)
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.editors, existing.editors)
            assertEquals(updated.viewers, existing.viewers + userId)
            assertEquals(updated.members, existing.members)
          case Member =>
            assertEquals(updated.version, existing.version)
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.editors, existing.editors)
            assertEquals(updated.viewers, existing.viewers)
            assertEquals(updated.members, existing.members + userId)
        }
    }

  test(
    "applyTo should add members and owners from the caller object except those that have been moved between buckets"
  ):
    Prop.forAll(idGen, versionGen, idGen, memberRoleGen) {
      case (projectId, version, userId, role) =>
        val existing = Project(id = projectId, version = version)
          .apply(
            EntityMembers(
              owners = Set(userId),
              editors = Set(userId),
              viewers = Set(userId),
              members = Set(userId)
            )
          )

        val update = Project(projectId, DocVersion.Exists).addMember(userId, role)

        val updated = existing.applyTo(update).asInstanceOf[Project]

        role match {
          case Owner =>
            assertEquals(updated.version, existing.version)
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.editors, existing.editors - userId)
            assertEquals(updated.viewers, existing.viewers - userId)
            assertEquals(updated.members, existing.members - userId)
          case Editor =>
            assertEquals(updated.version, existing.version)
            assertEquals(updated.owners, existing.owners - userId)
            assertEquals(updated.editors, existing.editors)
            assertEquals(updated.viewers, existing.viewers - userId)
            assertEquals(updated.members, existing.members - userId)
          case Viewer =>
            assertEquals(updated.version, existing.version)
            assertEquals(updated.owners, existing.owners - userId)
            assertEquals(updated.editors, existing.editors - userId)
            assertEquals(updated.viewers, existing.viewers)
            assertEquals(updated.members, existing.members - userId)
          case Member =>
            assertEquals(updated.version, existing.version)
            assertEquals(updated.owners, existing.owners - userId)
            assertEquals(updated.editors, existing.editors - userId)
            assertEquals(updated.viewers, existing.viewers - userId)
            assertEquals(updated.members, existing.members)
        }
    }

  test("removeMember should remove the given userId from the correct bucket in the doc"):
    Prop.forAll(partialProjectGen, idGen, memberRoleGen) { case (project, userId, role) =>
      val updated = project.addMember(userId, role)
      assertEquals(updated.removeMember(userId), project)
    }

  test("removeMember should do nothing if there's no member/owner with the given userId"):
    Prop.forAll(partialProjectGen, idGen, idGen, memberRoleGen) {
      case (project, existingUserId, toDeleteUserId, role) =>
        val updated = project.addMember(existingUserId, role)
        assertEquals(updated.removeMember(toDeleteUserId), updated)
    }
