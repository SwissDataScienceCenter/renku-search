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
import io.renku.events.v1.{ProjectAuthorizationAdded, ProjectMemberRole}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.ModelGenerators
import io.renku.search.model.projects.MemberRole
import io.renku.search.model.{Id, projects}
import io.renku.search.provision.project.ConcurrentUpdateSpec.testCases
import io.renku.search.provision.{BackgroundCollector, ProvisioningSuite}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.documents.{Project as ProjectDocument, SolrDocument}
import cats.effect.std.CountDownLatch
import io.renku.events.v1.ProjectCreated
import io.renku.queue.client.DataContentType
import io.renku.events.EventsGenerators
import scala.concurrent.duration.*

class ConcurrentUpdateSpec extends ProvisioningSuite:

  testCases.foreach { tc =>
    test(s"process concurrent events: $tc"):

      withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
        for {
          _ <- tc.dbState.create(solrClient)

          collector <- BackgroundCollector[SolrDocument](
            loadPartialOrEntity(solrClient, tc.projectId)
          )
          _ <- collector.start
          msgFiber <- handlers.singleStream.compile.drain.start

          latch <- CountDownLatch[IO](1)

          sendAuth <- (latch.await >> queueClient.enqueue(
            queueConfig.projectAuthorizationAdded,
            messageHeaderGen(ProjectAuthorizationAdded.SCHEMA$).generateOne,
            tc.authAdded
          )).start

          sendCreate <- (latch.await >> queueClient.enqueue(
            queueConfig.projectCreated,
            messageHeaderGen(ProjectCreated.SCHEMA$, DataContentType.Binary).generateOne,
            tc.projectCreated
          )).start

          _ <- latch.release
          _ <- List(sendAuth, sendCreate).traverse_(_.join)

          _ <- collector.waitUntil(
            docs =>
              scribe.info(s"Check for ${tc.expectedProject}")
              docs.contains(tc.expectedProject)
            ,
            timeout = 30.seconds
          )

          _ <- msgFiber.cancel
        } yield ()
      }
  }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)

object ConcurrentUpdateSpec:
  enum DbState:
    case Empty

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty => IO.unit

  final case class TestCase(
      name: String,
      dbState: DbState,
      user: Id,
      role: MemberRole,
      project: ProjectDocument
  ):
    val projectId = dbState match
      case DbState.Empty => project.id

    val projectCreated: ProjectCreated =
      EventsGenerators.projectCreatedGen("test-").generateOne.copy(id = projectId.value)

    val authAdded: ProjectAuthorizationAdded =
      ProjectAuthorizationAdded(
        projectId.value,
        user.value,
        ProjectMemberRole.valueOf(role.name.toUpperCase())
      )

    val expectedProject: SolrDocument = dbState match
      case DbState.Empty =>
        project.copy(
          owners = Set(user).filter(_ => role == MemberRole.Owner).toList,
          members = Set(user).filter(_ => role == MemberRole.Member).toList
        )

    override def toString = s"$name: ${user.value.take(6)}… db=$dbState"

  val testCases =
    for {
      role <- MemberRole.values.toList.take(1)
      proj = SolrDocumentGenerators.projectDocumentGen.generateOne
//      pproj = SolrDocumentGenerators.partialProjectGen.generateOne
      dbState <- List(DbState.Empty)
      userId = ModelGenerators.idGen.generateOne
    } yield TestCase(s"add $role", dbState, userId, role, proj)
