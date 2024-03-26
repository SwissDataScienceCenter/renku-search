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

import scala.concurrent.duration.*

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef

import io.renku.avro.codec.all.given
import io.renku.events.EventsGenerators.{stringGen, userAddedGen}
import io.renku.events.v1.{UserAdded, UserUpdated}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.documents.{CompoundId, EntityDocument}

class UserUpdatedProvisioningSpec extends ProvisioningSuite:
  (firstNameUpdate :: lastNameUpdate :: emailUpdate :: noUpdate :: Nil).foreach {
    case TestCase(name, updateF) =>
      test(s"can fetch events, decode them, and update in Solr in case of $name"):
        withMessageHandlers(queueConfig).use { case (handler, queueClient, solrClient) =>
          for
            solrDoc <- SignallingRef.of[IO, Option[EntityDocument]](None)

            provisioningFiber <- handler.userUpdated.compile.drain.start

            userAdded = userAddedGen(prefix = "user-update").generateOne
            _ <- solrClient.insert(Seq(userAdded.toSolrDocument.widen))

            userUpdated = updateF(userAdded)
            _ <- queueClient.enqueue(
              queueConfig.userUpdated,
              messageHeaderGen(UserUpdated.SCHEMA$).generateOne,
              userUpdated
            )

            docsCollectorFiber <-
              Stream
                .awakeEvery[IO](500 millis)
                .evalMap(_ =>
                  solrClient.findById[EntityDocument](
                    CompoundId.userEntity(Id(userAdded.id))
                  )
                )
                .evalMap(e => solrDoc.update(_ => e))
                .compile
                .drain
                .start

            _ <- solrDoc.waitUntil(
              _ contains userAdded.update(userUpdated).toSolrDocument
            )

            _ <- provisioningFiber.cancel
            _ <- docsCollectorFiber.cancel
          yield ()
        }
  }

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
