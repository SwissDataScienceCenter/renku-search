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

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.github.arainko.ducktape.*
import io.renku.avro.codec.AvroIO
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.projectCreatedGen
import io.renku.events.v1.{ProjectCreated, Visibility}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.{DataContentType, QueueSpec}
import io.renku.redis.client.RedisClientGenerators
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.search.model.{projects, users}
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.Project
import munit.CatsEffectSuite

import scala.concurrent.duration.*

class SearchProvisionerSpec extends CatsEffectSuite with QueueSpec with SearchSolrSpec:

  private val avro = AvroIO(ProjectCreated.SCHEMA$)

  test("can fetch events binary encoded, decode them, and send them to Solr"):
    val queue = RedisClientGenerators.queueNameGen.generateOne
    val clientId = RedisClientGenerators.clientIdGen.generateOne

    queueAndSolrClients.use { case (queueClient, solrClient) =>
      val provisioner =
        new SearchProvisionerImpl(clientId, queue, Resource.pure(queueClient), solrClient)
      for
        solrDocs <- SignallingRef.of[IO, Set[Project]](Set.empty)

        provisioningFiber <- provisioner.provisionSolr.start

        message1 = projectCreatedGen(prefix = "binary").generateOne
        _ <- queueClient.enqueue(
          queue,
          messageHeaderGen(ProjectCreated.SCHEMA$, DataContentType.Binary).generateOne,
          message1
        )

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ => solrClient.findProjects("*"))
            .flatMap(Stream.emits(_))
            .evalMap(d => solrDocs.update(_ + d))
            .compile
            .drain
            .start

        _ <- solrDocs.waitUntil(_ contains toSolrDocument(message1))

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  test("can fetch events JSON encoded, decode them, and send them to Solr"):
    val queue = RedisClientGenerators.queueNameGen.generateOne
    val clientId = RedisClientGenerators.clientIdGen.generateOne

    queueAndSolrClients.use { case (queueClient, solrClient) =>
      val provisioner =
        new SearchProvisionerImpl(clientId, queue, Resource.pure(queueClient), solrClient)
      for
        solrDocs <- SignallingRef.of[IO, Set[Project]](Set.empty)

        provisioningFiber <- provisioner.provisionSolr.start

        message1 = projectCreatedGen(prefix = "json").generateOne
        _ <- queueClient.enqueue(
          queue,
          messageHeaderGen(ProjectCreated.SCHEMA$, DataContentType.Json).generateOne,
          message1
        )

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ => solrClient.findProjects("*"))
            .flatMap(Stream.emits(_))
            .evalTap(IO.println)
            .evalMap(d => solrDocs.update(_ + d))
            .compile
            .drain
            .start

        _ <- solrDocs.waitUntil(_ contains toSolrDocument(message1))

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  private def queueAndSolrClients =
    withQueueClient() >>= withSearchSolrClient().tupleLeft

  private def toSolrDocument(created: ProjectCreated): Project =
    created
      .into[Project]
      .transform(
        Field.computed(
          _.visibility,
          pc => projects.Visibility.unsafeFromString(pc.visibility.name())
        )
      )

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
