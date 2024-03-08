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
import io.renku.events.EventsGenerators.{stringGen, userAddedGen}
import io.renku.events.v1.{UserAdded, UserUpdated}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.QueueSpec
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

class UserUpdatedProvisionerSpec
    extends CatsEffectSuite
    with QueueSpec
    with SearchSolrSpec:

  private val avro = AvroIO(UserUpdated.SCHEMA$)

  (firstNameUpdate :: lastNameUpdate :: emailUpdate :: noUpdate :: Nil).foreach {
    case TestCase(name, updateF) =>
      test(s"can fetch events, decode them, and update in Solr in case of $name"):
        val queue = RedisClientGenerators.queueNameGen.generateOne

        clientsAndProvisioning(queue).use { case (queueClient, solrClient, provisioner) =>
          for
            solrDocs <- SignallingRef.of[IO, Set[Entity]](Set.empty)

            provisioningFiber <- provisioner.provisioningProcess.start

            userAdded = userAddedGen(prefix = "update").generateOne
            _ <- solrClient.insert(Seq(userAdded.toSolrDocument.widen))

            userUpdated = updateF(userAdded)
            _ <- queueClient.enqueue(
              queue,
              messageHeaderGen(UserUpdated.SCHEMA$).generateOne,
              userUpdated
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

            _ <- solrDocs.waitUntil(
              _ contains userAdded.update(userUpdated).toSolrDocument
            )

            _ <- provisioningFiber.cancel
            _ <- docsCollectorFiber.cancel
          yield ()
        }
  }

  private lazy val queryUsers = Query(typeIs(EntityType.User))

  private def clientsAndProvisioning(queueName: QueueName) =
    (withQueueClient() >>= withSearchSolrClient().tupleLeft)
      .flatMap { case (rc, sc) =>
        UserUpdatedProvisioning
          .make[IO](
            queueName,
            withRedisClient.redisConfig,
            withSearchSolrClient.solrConfig
          )
          .map((rc, sc, _))
      }

  extension (added: UserAdded)
    def toSolrDocument: User = added.into[User].transform(Field.default(_.score))
    def update(updated: UserUpdated): UserAdded =
      val added1 = updated.firstName.fold(added)(v => added.copy(firstName = Some(v)))
      val added2 = updated.lastName.fold(added1)(v => added1.copy(lastName = Some(v)))
      updated.email.fold(added2)(v => added2.copy(email = Some(v)))

  private case class TestCase(name: String, f: UserAdded => UserUpdated)
  private lazy val firstNameUpdate = TestCase(
    "firstName update",
    ua =>
      UserUpdated(
        ua.id,
        stringGen(max = 5).generateOne.some,
        ua.lastName,
        ua.email
      )
  )
  private lazy val lastNameUpdate = TestCase(
    "lastName update",
    ua =>
      UserUpdated(
        ua.id,
        ua.firstName,
        stringGen(max = 5).generateOne.some,
        ua.email
      )
  )
  private lazy val emailUpdate = TestCase(
    "email update",
    ua =>
      UserUpdated(
        ua.id,
        ua.firstName,
        ua.lastName,
        stringGen(max = 5).map(v => s"v@host.com").generateOne.some
      )
  )
  private lazy val noUpdate = TestCase(
    "no update",
    ua => UserUpdated(ua.id, ua.firstName, ua.lastName, ua.email)
  )

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
