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

package io.renku.search.provision.project

import cats.effect.{IO, Resource}
import cats.syntax.all.*

import io.renku.events.EventsGenerators
import io.renku.events.EventsGenerators.*
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.*
import io.renku.search.model.Id
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.client.{SearchSolrClient, SolrDocumentGenerators}
import io.renku.search.solr.documents.EntityMembers
import io.renku.search.solr.documents.{
  PartialEntityDocument,
  Project as ProjectDocument,
  SolrDocument
}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class ProjectUpdatedProvisioningSpec extends ProvisioningSuite:

  ProjectUpdatedProvisioningSpec.testCases.foreach { tc =>
    test(s"can fetch events, decode them, and update in Solr: $tc"):
      withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
        for
          _ <- tc.dbState.create(solrClient)
          _ <- queueClient.enqueue(
            queueConfig.projectUpdated,
            EventsGenerators.eventMessageGen(Gen.const(tc.projectUpdated)).generateOne
          )

          _ <- handlers
            .makeProjectUpsert[ProjectUpdated](queueConfig.projectUpdated)
            .take(1)
            .compile
            .toList

          docs <- loadProjectPartialOrEntity(solrClient, tc.projectId)
          _ = docs.headOption match
            case Some(doc) =>
              assertEquals(doc.setVersion(DocVersion.Off), tc.expectedProject)
            case None => fail("no project document found")
        yield ()
      }
  }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)

object ProjectUpdatedProvisioningSpec:
  enum DbState:
    case Empty
    case Project(project: ProjectDocument)
    case PartialProject(project: PartialEntityDocument.Project)

    def projectId: Option[Id] = this match
      case Empty             => None
      case Project(p)        => p.id.some
      case PartialProject(p) => p.id.some

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty             => IO.unit
      case DbState.Project(p)        => solrClient.upsertSuccess(Seq(p))
      case DbState.PartialProject(p) => solrClient.upsertSuccess(Seq(p))

  case class TestCase(dbState: DbState, projectUpdated: ProjectUpdated):
    def projectId: Id = projectUpdated.id

    def expectedProject: SolrDocument =
      dbState match {
        case DbState.Empty => projectUpdated.toModel(DocVersion.Off)
        case DbState.Project(p) =>
          projectUpdated
            .toModel(p)
            .setVersion(DocVersion.Off)
            .setGroupMembers(EntityMembers.empty)
        case DbState.PartialProject(p) =>
          projectUpdated
            .toModel(p)
            .setVersion(DocVersion.Off)
            .setGroupMembers(EntityMembers.empty)
      }

  val testCases =
    val em = SolrDocumentGenerators.entityMembersGen
    val proj = em
      .flatMap(gm => SolrDocumentGenerators.projectDocumentGen.map(_.setGroupMembers(gm)))
      .generateOne
    val pproj = em
      .flatMap(gm => SolrDocumentGenerators.partialProjectGen.map(_.setGroupMembers(gm)))
      .generateOne
    val upd = EventsGenerators.projectUpdatedGen("proj-update").generateOne
    for
      dbState <- List(DbState.Empty, DbState.Project(proj), DbState.PartialProject(pproj))
      event = upd.withId(dbState.projectId.getOrElse(upd.id))
    yield TestCase(dbState, event)
