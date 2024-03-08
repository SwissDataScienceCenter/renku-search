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

package io.renku.search.provision.user

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.github.arainko.ducktape.*
import io.renku.avro.codec.AvroIO
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.userAddedGen
import io.renku.events.v1.UserAdded
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.{DataContentType, QueueSpec}
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.{QueueName, RedisClientGenerators}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.{EntityType, users}
import io.renku.search.query.Query
import io.renku.search.query.Query.Segment
import io.renku.search.query.Query.Segment.typeIs
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.EntityOps.*
import io.renku.search.solr.documents.{Entity, User}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

class UserAddedProvisioningSpec
    extends CatsEffectSuite
    with QueueSpec
    with SearchSolrSpec:

  private val avro = AvroIO(UserAdded.SCHEMA$)

  test("can fetch events binary encoded, decode them, and send them to Solr"):
    val queue = RedisClientGenerators.queueNameGen.generateOne

    clientsAndProvisioning(queue).use { case (queueClient, solrClient, provisioner) =>
      for
        solrDocs <- SignallingRef.of[IO, Set[Entity]](Set.empty)

        provisioningFiber <- provisioner.provisioningProcess.start

        message1 = userAddedGen(prefix = "binary").generateOne
        _ <- queueClient.enqueue(
          queue,
          messageHeaderGen(UserAdded.SCHEMA$, DataContentType.Binary).generateOne,
          message1
        )

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ => solrClient.queryEntity(queryUsers, 10, 0))
            .flatMap(qr => Stream.emits(qr.responseBody.docs))
            .evalMap(e => solrDocs.update(_ + e.noneScore))
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

    clientsAndProvisioning(queue).use { case (queueClient, solrClient, provisioner) =>
      for
        solrDocs <- SignallingRef.of[IO, Set[Entity]](Set.empty)

        provisioningFiber <- provisioner.provisioningProcess.start

        message1 = userAddedGen(prefix = "json").generateOne
        _ <- queueClient.enqueue(
          queue,
          messageHeaderGen(UserAdded.SCHEMA$, DataContentType.Json).generateOne,
          message1
        )

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ => solrClient.queryEntity(queryUsers, 10, 0))
            .flatMap(qr => Stream.emits(qr.responseBody.docs))
            .evalTap(IO.println)
            .evalMap(e => solrDocs.update(_ + e.noneScore))
            .compile
            .drain
            .start

        _ <- solrDocs.waitUntil(_ contains toSolrDocument(message1))

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  private lazy val queryUsers = Query(typeIs(EntityType.User))

  private def clientsAndProvisioning(queueName: QueueName) =
    (withQueueClient() >>= withSearchSolrClient().tupleLeft)
      .flatMap { case (rc, sc) =>
        UserAddedProvisioning
          .make[IO](
            queueName,
            withRedisClient.redisConfig,
            withSearchSolrClient.solrConfig
          )
          .map((rc, sc, _))
      }

  private def toSolrDocument(added: UserAdded): User =
    added.into[User].transform(Field.default(_.score))

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
