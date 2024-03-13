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
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.userAddedGen
import io.renku.events.v1.UserAdded
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.QueueSpec
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.{QueueName, RedisClientGenerators}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.{Entity, User}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

class UserAddedProvisioningSpec
    extends CatsEffectSuite
    with QueueSpec
    with SearchSolrSpec
    with UserSyntax:

  test("can fetch events, decode them, and send them to Solr"):
    val queue = RedisClientGenerators.queueNameGen.generateOne

    clientsAndProvisioning(queue).use { case (queueClient, solrClient, provisioner) =>
      for
        solrDoc <- SignallingRef.of[IO, Option[Entity]](None)

        provisioningFiber <- provisioner.provisioningProcess.start

        userAdded = userAddedGen(prefix = "user-added").generateOne
        _ <- queueClient.enqueue(
          queue,
          messageHeaderGen(UserAdded.SCHEMA$).generateOne,
          userAdded
        )

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ => solrClient.findById[User](userAdded.id))
            .evalMap(e => solrDoc.update(_ => e))
            .compile
            .drain
            .start

        _ <- solrDoc.waitUntil(_ contains userAdded.toSolrDocument)

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

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

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
