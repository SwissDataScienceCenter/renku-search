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

import scala.concurrent.duration.*

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef

import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.*
import io.renku.events.v1.{ProjectAuthorizationUpdated, ProjectMemberRole}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.{Id, projects}
import io.renku.search.solr.documents.{Entity, Project}
import munit.CatsEffectSuite

class AuthorizationUpdatedProvisioningSpec extends ProvisioningSuite:

  (memberAdded :: ownerAdded :: noUpdate :: Nil)
    .foreach { case TestCase(name, updateF) =>
      test(s"can fetch events, decode them, and update docs in Solr in case of $name"):
        withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
          for
            solrDocs <- SignallingRef.of[IO, Set[Project]](Set.empty)

            provisioningFiber <- handlers.projectAuthUpdated.compile.drain.start

            projectDoc = projectCreatedGen("member-add").generateOne.toSolrDocument
            _ <- solrClient.insert(Seq(projectDoc.widen))

            authAdded = updateF(projectDoc)
            _ <- queueClient.enqueue(
              queueConfig.projectAuthorizationUpdated,
              messageHeaderGen(ProjectAuthorizationUpdated.SCHEMA$).generateOne,
              authAdded
            )

            docsCollectorFiber <-
              Stream
                .awakeEvery[IO](500 millis)
                .evalMap(_ => solrClient.findById[Project](projectDoc.id))
                .evalMap(_.fold(().pure[IO])(e => solrDocs.update(_ => Set(e))))
                .compile
                .drain
                .start

            _ <- solrDocs.waitUntil(
              _ contains projectDoc.addMember(
                Id(authAdded.userId),
                projects.MemberRole.unsafeFromString(authAdded.role.name())
              )
            )

            _ <- provisioningFiber.cancel
            _ <- docsCollectorFiber.cancel
          yield ()
        }
    }

  private case class TestCase(name: String, f: Project => ProjectAuthorizationUpdated)

  private lazy val memberAdded = TestCase(
    "member added",
    pd =>
      projectAuthorizationUpdatedGen(pd.id.value, ProjectMemberRole.MEMBER).generateOne
  )

  private lazy val ownerAdded = TestCase(
    "owner added",
    pd => projectAuthorizationUpdatedGen(pd.id.value, ProjectMemberRole.OWNER).generateOne
  )

  private lazy val noUpdate = TestCase(
    "no update",
    pd =>
      ProjectAuthorizationUpdated(
        pd.id.value,
        pd.owners.head.value,
        ProjectMemberRole.OWNER
      )
  )

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
