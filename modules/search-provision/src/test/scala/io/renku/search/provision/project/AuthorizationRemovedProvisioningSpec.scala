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

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef

import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.*
import io.renku.events.v1.*
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.QueueSpec
import io.renku.redis.client.{QueueName, RedisClientGenerators}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.Project
import munit.CatsEffectSuite

class AuthorizationRemovedProvisioningSpec
    extends CatsEffectSuite
    with QueueSpec
    with SearchSolrSpec
    with ProjectSyntax:

  test("can fetch events, decode them, and update docs in Solr"):
    val queue = RedisClientGenerators.queueNameGen.generateOne

    clientsAndProvisioning(queue).use { case (queueClient, solrClient, provisioner) =>
      for
        solrDocs <- SignallingRef.of[IO, Set[Project]](Set.empty)

        provisioningFiber <- provisioner.provisioningProcess.start

        projectDoc = projectCreatedGen("member-remove").generateOne.toSolrDocument
        _ <- solrClient.insert(Seq(projectDoc.widen))

        authRemoved = ProjectAuthorizationRemoved(
          projectDoc.id.value,
          projectDoc.createdBy.value
        )
        _ <- queueClient.enqueue(
          queue,
          messageHeaderGen(ProjectAuthorizationRemoved.SCHEMA$).generateOne,
          authRemoved
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
          _ contains projectDoc.removeMember(projectDoc.createdBy)
        )

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  private def clientsAndProvisioning(queueName: QueueName) =
    (withQueueClient() >>= withSearchSolrClient().tupleLeft)
      .flatMap { case (rc, sc) =>
        AuthorizationRemovedProvisioning
          .make[IO](
            queueName,
            withRedisClient.redisConfig,
            withSearchSolrClient.solrConfig
          )
          .map((rc, sc, _))
      }

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
