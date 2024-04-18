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

import cats.effect.std.CountDownLatch
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators
import io.renku.events.v1.{ProjectAuthorizationAdded, ProjectCreated, ProjectMemberRole, Visibility}
import io.renku.queue.client.DataContentType
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.{Id, MemberRole, ModelGenerators}
import io.renku.search.provision.handler.{DocumentMerger, ShowInstances}
import io.renku.search.provision.project.ConcurrentUpdateSpec.testCases
import io.renku.search.provision.{BackgroundCollector, ProvisioningSuite}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.{SolrDocument, Project as ProjectDocument}

import scala.concurrent.duration.*

class ConcurrentUpdateSpec extends ProvisioningSuite with ShowInstances:
  testCases.foreach { tc =>
    test(s"process concurrent events: $tc"):

      withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
        for {
          _ <- tc.dbState.create(solrClient)

          collector <- BackgroundCollector[SolrDocument](
            loadPartialOrEntity(solrClient, tc.projectId)
          )
          _ <- collector.start
          msgFiber <- List(handlers.projectCreated, handlers.projectAuthAdded).traverse(
            _.compile.drain.start
          )

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
          .copy(
            owners = Set(user).filter(_ => role == MemberRole.Owner).toList,
            members = Set(user).filter(_ => role == MemberRole.Member).toList
          )

    val projectId = dbState match
      case DbState.Empty => expectedProject.id

    val authAdded: ProjectAuthorizationAdded =
      ProjectAuthorizationAdded(
        projectId.value,
        user.value,
        ProjectMemberRole.valueOf(role.name.toUpperCase())
      )

    def checkExpected(doc: SolrDocument): Boolean =
      val e = expectedProject
      doc match
        case p: ProjectDocument =>
          (e.id, e.owners, e.members, e.slug) == (p.id, p.owners, p.members, p.slug)
        case _ => false

    override def toString = s"$name: ${user.value.take(6)}… db=$dbState"

  val testCases =
    for {
      dbState <- List(DbState.Empty)
      userId = ModelGenerators.idGen.generateOne
      role <- MemberRole.values.toList.take(1)
      proj = EventsGenerators.projectCreatedGen("test-concurrent").generateOne
    } yield TestCase(s"add $role", dbState, userId, role, proj)
