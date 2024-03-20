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
import fs2.Stream
import fs2.concurrent.SignallingRef

import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.userAddedGen
import io.renku.events.v1.UserAdded
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.documents.{CompoundId, EntityDocument}
import munit.CatsEffectSuite

class UserAddedProvisioningSpec extends ProvisioningSuite:

  test("can fetch events, decode them, and send them to Solr"):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        solrDoc <- SignallingRef.of[IO, Option[EntityDocument]](None)

        provisioningFiber <- handlers.userAdded.compile.drain.start

        userAdded = userAddedGen(prefix = "user-added").generateOne
        _ <- queueClient.enqueue(
          queueConfig.userAdded,
          messageHeaderGen(UserAdded.SCHEMA$).generateOne,
          userAdded
        )

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ =>
              solrClient.findById[EntityDocument](CompoundId.userEntity(Id(userAdded.id)))
            )
            .evalMap(e => solrDoc.update(_ => e))
            .compile
            .drain
            .start

        _ <- solrDoc.waitUntil(_ contains userAdded.toSolrDocument)

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
