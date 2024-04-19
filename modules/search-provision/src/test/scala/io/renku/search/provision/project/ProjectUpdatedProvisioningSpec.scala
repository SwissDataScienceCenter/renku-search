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

import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.*
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.ProjectUpdated
import io.renku.search.model.Id
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.provision.BackgroundCollector
import io.renku.search.solr.documents.SolrDocument
import io.renku.events.EventsGenerators
import io.renku.solr.client.DocVersion
import io.renku.queue.client.SchemaVersion

class ProjectUpdatedProvisioningSpec extends ProvisioningSuite:

  ProjectUpdatedProvisioningSpec.testCases.foreach { tc =>
    test(s"can fetch events, decode them, and update in Solr: $tc"):
      withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
        for
          _ <- tc.dbState.create(solrClient)

          collector <- BackgroundCollector[SolrDocument](
            loadPartialOrEntity(solrClient, tc.projectId)
          )
          _ <- collector.start

          provisioningFiber <- handlers.projectUpdated.compile.drain.start

          _ <- queueClient.enqueue(
            queueConfig.projectUpdated,
            messageHeaderGen(tc.projectUpdated.schema).generateOne
              .copy(schemaVersion = SchemaVersion(tc.projectUpdated.version.head.name)),
            tc.projectUpdated
          )

          _ <- collector.waitUntil(docs => docs.exists(tc.checkExpected))

          _ <- provisioningFiber.cancel
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

    def checkExpected(d: SolrDocument): Boolean =
      dbState match
        case DbState.Empty =>
          d match
            case n: ProjectDocument => false
            case n: PartialEntityDocument.Project =>
              n.setVersion(DocVersion.Off) == projectUpdated.toModel(DocVersion.Off)

        case DbState.Project(p) =>
          d match
            case n: ProjectDocument =>
              projectUpdated.toModel(p).setVersion(DocVersion.Off) == n.setVersion(
                DocVersion.Off
              )
            case _ => false

        case DbState.PartialProject(p) =>
          d match
            case n: PartialEntityDocument.Project =>
              projectUpdated.toModel(p).setVersion(DocVersion.Off) == n.setVersion(
                DocVersion.Off
              )

            case _ => false

  val testCases =
    val proj = SolrDocumentGenerators.projectDocumentGen.generateOne
    val pproj = SolrDocumentGenerators.partialProjectGen.generateOne
    val upd = EventsGenerators.projectUpdatedGen("proj-update").generateOne
    for
      dbState <- List(DbState.Empty, DbState.Project(proj), DbState.PartialProject(pproj))
      event = upd.withId(dbState.projectId.getOrElse(upd.id))
    yield TestCase(dbState, event)
