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

import scala.concurrent.duration.*

import cats.effect.std.CountDownLatch
import cats.effect.{IO, Resource}
import cats.syntax.all.*

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.*
import io.renku.search.events.ProjectMemberAdded
import io.renku.search.model.{Id, MemberRole, ModelGenerators}
import io.renku.search.provision.ConcurrentUpdateSpec.testCases
import io.renku.search.provision.handler.DocumentMerger
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.{
  EntityMembers,
  Project as ProjectDocument,
  SolrDocument
}
import org.scalacheck.Gen

class ConcurrentUpdateSpec extends ProvisioningSuite:
  testCases.foreach { tc =>
    test(s"process concurrent events: $tc"):

      withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
        for {
          _ <- tc.dbState.create(solrClient)

          collector <- BackgroundCollector[SolrDocument](
            loadProjectPartialOrEntity(solrClient, tc.projectId)
          )
          _ <- collector.start
          msgFiber <- List(handlers.projectCreated, handlers.projectAuthAdded)
            .traverse(_.compile.drain.start)

          latch <- CountDownLatch[IO](1)

          sendAuth <- (latch.await >> queueClient.enqueue(
            queueConfig.projectAuthorizationAdded,
            EventsGenerators.eventMessageGen(Gen.const(tc.authAdded)).generateOne
          )).start

          sendCreate <- (latch.await >> queueClient.enqueue(
            queueConfig.projectCreated,
            EventsGenerators.eventMessageGen(Gen.const(tc.projectCreated)).generateOne
          )).start

          _ <- latch.release
          _ <- List(sendAuth, sendCreate).traverse_(_.join)

          _ <- collector.waitUntil(
            docs =>
              scribe.debug(s"Check for ${tc.expectedProject}")
              docs.exists(tc.checkExpected)
            ,
            timeout = 30.seconds
          )

          _ <- msgFiber.traverse_(_.cancel)
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
      projectCreated: ProjectCreated
  ):
    val expectedProject: ProjectDocument = dbState match
      case DbState.Empty =>
        val p = DocumentMerger[ProjectCreated].create(projectCreated).get
        p.asInstanceOf[ProjectDocument]
          .setMembers(EntityMembers.empty.addMember(user, role))

    val projectId: Id = dbState match
      case DbState.Empty => expectedProject.id

    val authAdded: ProjectMemberAdded =
      ProjectMemberAdded(projectId, user, role)

    def checkExpected(doc: SolrDocument): Boolean =
      val e = expectedProject
      doc match
        case p: ProjectDocument =>
          (e.id, e.slug, e.toEntityMembers) == (p.id, p.slug, p.toEntityMembers)
        case _ => false

    override def toString = s"$name: ${user.value.take(6)}… db=$dbState"

  val testCases =
    for {
      dbState <- List(DbState.Empty)
      userId = ModelGenerators.idGen.generateOne
      role <- MemberRole.values.toList.take(1)
      proj = EventsGenerators.projectCreatedGen("test-concurrent").generateOne
    } yield TestCase(s"add $role", dbState, userId, role, proj)
