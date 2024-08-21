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

package io.renku.search.provision
package project

import cats.effect.IO

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.ProjectMemberUpdated
import io.renku.search.model.{Id, MemberRole, ModelGenerators}
import io.renku.search.provision.project.AuthorizationUpdatedProvisioningSpec.testCases
import io.renku.search.solr.client.{SearchSolrClient, SolrDocumentGenerators}
import io.renku.search.solr.documents.{
  PartialEntityDocument,
  Project as ProjectDocument,
  SolrDocument
}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen
//import scala.concurrent.duration.*

class AuthorizationUpdatedProvisioningSpec extends ProvisioningSuite:
  testCases.foreach { tc =>
    test(s"can fetch events, decode them, and update docs in Solr: $tc"):
      for {
        services <- IO(testServices())
        handler = services.syncHandler(queueConfig.projectAuthorizationUpdated)
        queueClient = services.queueClient
        solrClient = services.searchClient

        _ <- tc.dbState.create(solrClient)

        _ <- queueClient.enqueue(
          queueConfig.projectAuthorizationUpdated,
          EventsGenerators.eventMessageGen(Gen.const(tc.authUpdated)).generateOne
        )
//        _ <- IO.sleep(100.millis)
        result <- handler.create.take(1).compile.lastOrError
        _ = assert(result.asUpsert.exists(_.isSuccess))

        found <- loadProjectPartialOrEntity(solrClient, tc.projectId)

        _ = assert(tc.checkExpected(found.toSet))
      } yield ()
  }

object AuthorizationUpdatedProvisioningSpec:
  enum DbState:
    case Empty
    case Project(project: ProjectDocument)
    case PartialProject(project: PartialEntityDocument.Project)

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty             => IO.unit
      case DbState.Project(p)        => solrClient.upsert(Seq(p))
      case DbState.PartialProject(p) => solrClient.upsert(Seq(p))

  case class TestCase(name: String, dbState: DbState, user: Id, role: MemberRole):
    val projectId = dbState match
      case DbState.Empty             => ModelGenerators.idGen.generateOne
      case DbState.Project(p)        => p.id
      case DbState.PartialProject(p) => p.id

    val authUpdated: ProjectMemberUpdated =
      EventsGenerators.projectMemberUpdated(projectId, user, role).generateOne

    val expectedProject: Set[SolrDocument] = dbState match
      case DbState.Empty =>
        Set(
          PartialEntityDocument.Project(
            id = projectId,
            version = DocVersion.NotExists,
            owners = Set(user).filter(_ => role == MemberRole.Owner),
            members = Set(user).filter(_ => role == MemberRole.Member)
          )
        )

      case DbState.Project(p) =>
        Set(p.modifyEntityMembers(_.removeMember(user).addMember(user, role)))

      case DbState.PartialProject(p) =>
        Set(p.modifyEntityMembers(_.removeMember(user).addMember(user, role)))

    def checkExpected(d: Set[SolrDocument]): Boolean =
      if (d.isEmpty) sys.error("Empty document set passed to check")
      if (d.size == 1 && expectedProject.size == 1)
        munit.Assertions.assertEquals(
          d.head.setVersion(DocVersion.Off),
          expectedProject.head.setVersion(DocVersion.Off)
        )
        true
      else
        expectedProject
          .map(_.setVersion(DocVersion.Off))
          .diff(d.map(_.setVersion(DocVersion.Off)))
          .isEmpty

    override def toString = s"$name: ${user.value.take(6)}… db=$dbState"

  val testCases =
    for {
      role <- MemberRole.valuesV1.toList
      proj = SolrDocumentGenerators.projectDocumentGenForInsert.generateOne
      pproj = SolrDocumentGenerators.partialProjectGen.generateOne
      dbState <- List(DbState.Empty, DbState.Project(proj), DbState.PartialProject(pproj))
      userId = ModelGenerators.idGen.generateOne
    } yield TestCase(s"add $role", dbState, userId, role)
