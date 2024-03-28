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
import io.renku.events.EventsGenerators.*
import io.renku.events.v1.*
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.ModelGenerators.projectMemberRoleGen
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.QueueMessageDecoder
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.{CompoundId, EntityDocument}

class UserRemovedProcessSpec extends ProvisioningSuite:
  test(
    "can fetch events, decode them, and remove from Solr relevant User document " +
      "and issue ProjectAuthorizationRemoved events for all affected projects"
  ):
    val messageDecoder = QueueMessageDecoder[IO, ProjectAuthorizationRemoved]

    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        solrDoc <- SignallingRef.of[IO, Option[EntityDocument]](None)
        authRemovalEvents <- SignallingRef.of[IO, Set[ProjectAuthorizationRemoved]](
          Set.empty
        )

        provisioningFiber <- handlers.userRemoved.compile.drain.start

        user = userDocumentGen.generateOne
        affectedProjects = projectCreatedGen("affected")
          .map(_.toSolrDocument.addMember(user.id, projectMemberRoleGen.generateOne))
          .generateList(min = 20, max = 25)
        notAffectedProject = projectCreatedGen(
          "not-affected"
        ).generateOne.toSolrDocument
        _ <- solrClient.insert(user :: notAffectedProject :: affectedProjects)

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ =>
              solrClient.findById[EntityDocument](CompoundId.userEntity(user.id))
            )
            .evalMap(e => solrDoc.update(_ => e))
            .compile
            .drain
            .start
        eventsCollectorFiber <-
          queueClient
            .acquireEventsStream(queueConfig.projectAuthorizationRemoved, 1, None)
            .evalMap(messageDecoder.decodeMessage)
            .evalMap(e => authRemovalEvents.update(_ ++ e))
            .compile
            .drain
            .start

        _ <- scribe.cats.io.info("Waiting for test documents to be inserted")
        _ <- solrDoc.waitUntil(_.nonEmpty)
        _ <- scribe.cats.io.info("Test documents inserted")

        _ <- queueClient.enqueue(
          queueConfig.userRemoved,
          messageHeaderGen(UserRemoved.SCHEMA$).generateOne,
          UserRemoved(user.id.value)
        )

        _ <- solrDoc.waitUntil(_.isEmpty)
        _ <- scribe.cats.io.info(
          "User has been removed. Waiting for project auth removals"
        )

        expectedAuthRemovals =
          affectedProjects
            .map(ap => ProjectAuthorizationRemoved(ap.id.value, user.id.value))
            .toSet

        _ <- authRemovalEvents.waitUntil(x => expectedAuthRemovals.diff(x).isEmpty)

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
        _ <- eventsCollectorFiber.cancel
      yield ()
    }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
