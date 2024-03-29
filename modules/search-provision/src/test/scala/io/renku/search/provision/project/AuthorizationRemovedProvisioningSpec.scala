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

import io.renku.avro.codec.encoders.all.given
import io.renku.events.v1.*
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.model.ModelGenerators
import io.renku.search.provision.project.AuthorizationRemovedProvisioningSpec.testCases
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.search.solr.documents.{Project as ProjectDocument, *}

class AuthorizationRemovedProvisioningSpec extends ProvisioningSuite:
  testCases.foreach { tc =>
    test(s"can fetch events, decode them, and update docs in Solr: $tc"):

      withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
        for {
          _ <- tc.dbState.create(solrClient)

          collector <- BackgroundCollector[SolrDocument](
            loadPartialOrEntity(solrClient, tc.projectId)
          )
          _ <- collector.start

          provisioningFiber <- handlers.projectAuthRemoved.compile.drain.start

          _ <- queueClient.enqueue(
            queueConfig.projectAuthorizationRemoved,
            messageHeaderGen(ProjectAuthorizationRemoved.SCHEMA$).generateOne,
            tc.authRemoved
          )
          _ <- collector.waitUntil(docs =>
            scribe.info(s"Check for ${tc.expectedProject}")
            tc.expectedProject.diff(docs).isEmpty
          )

          _ <- provisioningFiber.cancel
        } yield ()
      }
  }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)

object AuthorizationRemovedProvisioningSpec:
  enum DbState:
    case Empty
    case Project(project: ProjectDocument)
    case PartialProject(project: PartialEntityDocument.Project)

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty             => IO.unit
      case DbState.Project(p)        => solrClient.insert(Seq(p))
      case DbState.PartialProject(p) => solrClient.insert(Seq(p))

  case class TestCase(dbState: DbState, user: Id):
    val projectId = dbState match
      case DbState.Empty             => ModelGenerators.idGen.generateOne
      case DbState.Project(p)        => p.id
      case DbState.PartialProject(p) => p.id

    val authRemoved: ProjectAuthorizationRemoved =
      ProjectAuthorizationRemoved(projectId.value, user.value)

    val expectedProject: Set[SolrDocument] = dbState match
      case DbState.Empty =>
        Set.empty

      case DbState.Project(p) =>
        Set(p.removeMember(user))

      case DbState.PartialProject(p) =>
        Set(p.remove(user))

    override def toString = s"AuthRemove(${user.value.take(6)}… db=$dbState)"

  private val testCases =
    val proj = SolrDocumentGenerators.projectDocumentGen.generateOne
    val pproj = SolrDocumentGenerators.partialProjectGen.generateOne
    val userId = ModelGenerators.idGen.generateOne
    val dbState =
      List(DbState.Empty, DbState.Project(proj), DbState.PartialProject(pproj))
    dbState.map(TestCase(_, userId))
