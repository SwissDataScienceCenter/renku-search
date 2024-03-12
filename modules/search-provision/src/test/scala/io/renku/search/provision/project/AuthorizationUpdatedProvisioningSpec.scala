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

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.github.arainko.ducktape.*
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.*
import io.renku.events.v1.{ProjectAuthorizationUpdated, ProjectCreated, ProjectMemberRole}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.QueueSpec
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.{QueueName, RedisClientGenerators}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.{projects, users}
import io.renku.search.provision.TypeTransformers.given
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.{Entity, Project}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

class AuthorizationUpdatedProvisioningSpec
    extends CatsEffectSuite
    with QueueSpec
    with SearchSolrSpec:

  (memberAdded :: ownerAdded :: noUpdate :: Nil)
    .foreach { case TestCase(name, updateF) =>
      test(s"can fetch events, decode them, and update docs in Solr in case of $name"):
        val queue = RedisClientGenerators.queueNameGen.generateOne

        clientsAndProvisioning(queue).use { case (queueClient, solrClient, provisioner) =>
          for
            solrDocs <- SignallingRef.of[IO, Set[Project]](Set.empty)

            provisioningFiber <- provisioner.provisioningProcess.start

            projectDoc = projectCreatedGen("member-add").generateOne.toSolrDocument
            _ <- solrClient.insert(Seq(projectDoc.widen))

            authAdded = updateF(projectDoc)
            _ <- queueClient.enqueue(
              queue,
              messageHeaderGen(ProjectAuthorizationUpdated.SCHEMA$).generateOne,
              authAdded
            )

            docsCollectorFiber <-
              Stream
                .awakeEvery[IO](500 millis)
                .evalMap(_ => solrClient.findById[Project](projectDoc.id.value))
                .evalMap(_.fold(().pure[IO])(e => solrDocs.update(_ => Set(e))))
                .compile
                .drain
                .start

            _ <- solrDocs.waitUntil(
              _ contains projectDoc.addMember(
                users.Id(authAdded.userId),
                projects.MemberRole.unsafeFromString(authAdded.role.name())
              )
            )

            _ <- provisioningFiber.cancel
            _ <- docsCollectorFiber.cancel
          yield ()
        }
    }

  private def clientsAndProvisioning(queueName: QueueName) =
    (withQueueClient() >>= withSearchSolrClient().tupleLeft)
      .flatMap { case (rc, sc) =>
        AuthorizationUpdatedProvisioning
          .make[IO](
            queueName,
            withRedisClient.redisConfig,
            withSearchSolrClient.solrConfig
          )
          .map((rc, sc, _))
      }

  extension (created: ProjectCreated)
    def toSolrDocument: Project = created
      .into[Project]
      .transform(
        Field.computed(_.owners, pc => List(users.Id(pc.createdBy))),
        Field.default(_.members),
        Field.default(_.score)
      )

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
