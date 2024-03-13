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

import scala.concurrent.duration.*
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.renku.avro.codec.AvroIO
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.projectCreatedGen
import io.renku.events.v1.{ProjectCreated, Visibility}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.{DataContentType, QueueSpec}
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.{QueueName, RedisClientGenerators}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.{Entity, Project}
import munit.CatsEffectSuite

class ProjectCreatedProvisioningSpec
    extends CatsEffectSuite
    with QueueSpec
    with SearchSolrSpec
    with ProjectSyntax:

  private val avro = AvroIO(ProjectCreated.SCHEMA$)

  test("can fetch events binary encoded, decode them, and send them to Solr"):
    val queue = RedisClientGenerators.queueNameGen.generateOne

    clientsAndProvisioning(queue).use { case (queueClient, solrClient, provisioner) =>
      for
        solrDocs <- SignallingRef.of[IO, Set[Entity]](Set.empty)

        provisioningFiber <- provisioner.provisioningProcess.start

        created = projectCreatedGen(prefix = "binary").generateOne
        _ <- queueClient.enqueue(
          queue,
          messageHeaderGen(ProjectCreated.SCHEMA$, DataContentType.Binary).generateOne,
          created
        )

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ => solrClient.findById[Project](Id(created.id)))
            .evalMap(_.fold(().pure[IO])(e => solrDocs.update(_ => Set(e))))
            .compile
            .drain
            .start

        _ <- solrDocs.waitUntil(_ contains toSolrDocument(created))

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  test("can fetch events JSON encoded, decode them, and send them to Solr"):
    val queue = RedisClientGenerators.queueNameGen.generateOne

    clientsAndProvisioning(queue).use { case (queueClient, solrClient, provisioner) =>
      for
        solrDocs <- SignallingRef.of[IO, Set[Entity]](Set.empty)

        provisioningFiber <- provisioner.provisioningProcess.start

        created = projectCreatedGen(prefix = "json").generateOne
        _ <- queueClient.enqueue(
          queue,
          messageHeaderGen(ProjectCreated.SCHEMA$, DataContentType.Json).generateOne,
          created
        )

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ => solrClient.findById[Project](Id(created.id)))
            .evalMap(_.fold(().pure[IO])(e => solrDocs.update(_ => Set(e))))
            .compile
            .drain
            .start

        _ <- solrDocs.waitUntil(_ contains toSolrDocument(created))

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  private def clientsAndProvisioning(queueName: QueueName) =
    (withQueueClient() >>= withSearchSolrClient().tupleLeft)
      .flatMap { case (rc, sc) =>
        ProjectCreatedProvisioning
          .make[IO](
            queueName,
            withRedisClient.redisConfig,
            withSearchSolrClient.solrConfig
          )
          .map((rc, sc, _))
      }

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
