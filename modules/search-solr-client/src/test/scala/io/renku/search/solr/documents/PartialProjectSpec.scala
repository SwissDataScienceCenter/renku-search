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
import io.renku.search.model.ModelGenerators.{idGen, projectMemberRoleGen}
import io.renku.search.model.projects
import io.renku.search.model.projects.MemberRole.{Member, Owner}
import io.renku.search.solr.client.SolrDocumentGenerators.partialProjectGen
import io.renku.search.solr.documents.PartialEntityDocument.Project
import io.renku.solr.client.DocVersion
import io.renku.solr.client.SolrClientGenerator.versionGen
import munit.{FunSuite, ScalaCheckSuite}
import org.scalacheck.Prop

class PartialProjectSpec extends ScalaCheckSuite with GeneratorSyntax:

  test("add should add the userId to the relevant bucket"):
    Prop.forAll(partialProjectGen, idGen, projectMemberRoleGen) {
      case (existing, userId, role) =>
        val updated = existing.add(userId, role)

        role match {
          case Owner =>
            assertEquals(updated.owners, existing.owners + userId)
            assertEquals(updated.members, existing.members)
          case Member =>
            assertEquals(updated.members, existing.members + userId)
            assertEquals(updated.owners, existing.owners)
        }
    }

  test(
    "add should add the userId to the relevant bucket and remove from the other bucket if existed"
  ):
    Prop.forAll(idGen, versionGen, idGen, projectMemberRoleGen) {
      case (projectId, version, userId, role) =>
        val existing = Project(
          id = projectId,
          _version_ = version,
          owners = Set(userId),
          members = Set(userId)
        )

        val updated = existing.add(userId, role)

        role match {
          case Owner =>
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.members, existing.members - userId)
          case Member =>
            assertEquals(updated.members, existing.members)
            assertEquals(updated.owners, existing.owners - userId)
        }
    }

  test("applyTo should add members and owners from the caller object"):
    Prop.forAll(partialProjectGen, idGen, projectMemberRoleGen) {
      case (existing, userId, role) =>
        val update = Project(existing.id, existing.`_version_`).add(userId, role)

        val updated = existing.applyTo(update).asInstanceOf[Project]

        role match {
          case Owner =>
            assertEquals(updated.`_version_`, existing.`_version_`)
            assertEquals(updated.owners, existing.owners + userId)
            assertEquals(updated.members, existing.members)
          case Member =>
            assertEquals(updated.`_version_`, existing.`_version_`)
            assertEquals(updated.members, existing.members + userId)
            assertEquals(updated.owners, existing.owners)
        }
    }

  test(
    "applyTo should add members and owners from the caller object except those that have been moved between buckets"
  ):
    Prop.forAll(idGen, versionGen, idGen, projectMemberRoleGen) {
      case (projectId, version, userId, role) =>
        val existing = Project(
          id = projectId,
          _version_ = version,
          owners = Set(userId),
          members = Set(userId)
        )

        val update = Project(projectId, DocVersion.Exists).add(userId, role)

        val updated = existing.applyTo(update).asInstanceOf[Project]

        role match {
          case Owner =>
            assertEquals(updated.`_version_`, existing.`_version_`)
            assertEquals(updated.owners, existing.owners)
            assertEquals(updated.members, existing.members - userId)
          case Member =>
            assertEquals(updated.`_version_`, existing.`_version_`)
            assertEquals(updated.members, existing.members)
            assertEquals(updated.owners, existing.owners - userId)
        }
    }

  test("removeMember should remove the given userId from the correct bucket in the doc"):
    Prop.forAll(partialProjectGen, idGen, projectMemberRoleGen) {
      case (project, userId, role) =>
        val updated = project.add(userId, role)
        assertEquals(updated.remove(userId), project)
    }

  test("removeMember should do nothing if there's no member/owner with the given userId"):
    Prop.forAll(partialProjectGen, idGen, idGen, projectMemberRoleGen) {
      case (project, existingUserId, toDeleteUserId, role) =>
        val updated = project.add(existingUserId, role)
        assertEquals(updated.remove(toDeleteUserId), updated)
    }
