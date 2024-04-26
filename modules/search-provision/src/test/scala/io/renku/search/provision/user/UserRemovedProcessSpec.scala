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
import io.renku.events.EventsGenerators.*
import io.renku.search.events.*
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.ModelGenerators.memberRoleGen
import io.renku.search.provision.events.syntax.*
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.{CompoundId, EntityDocument}
import io.renku.solr.client.DocVersion
import io.renku.events.EventsGenerators
import org.scalacheck.Gen

class UserRemovedProcessSpec extends ProvisioningSuite:
  private val logger = scribe.cats.io

  test(
    "can fetch events, decode them, and remove from Solr relevant User document " +
      "and issue ProjectMemberRemoved events for all affected projects"
  ):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        solrDoc <- SignallingRef.of[IO, Option[EntityDocument]](None)
        authRemovalEvents <- SignallingRef.of[IO, Set[ProjectMemberRemoved]](
          Set.empty
        )

        provisioningFiber <- handlers.userRemoved.compile.drain.start

        user = userDocumentGen.generateOne
        affectedProjects = projectCreatedGen("affected")
          .map(
            _.toModel(DocVersion.Off).addMember(user.id, memberRoleGen.generateOne)
          )
          .generateList(min = 20, max = 25)
        notAffectedProject = projectCreatedGen(
          "not-affected"
        ).generateOne.toModel(DocVersion.Off)
        _ <- solrClient.upsert(user :: notAffectedProject :: affectedProjects)

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
            .acquireMessageStream[ProjectMemberRemoved](
              queueConfig.projectAuthorizationRemoved,
              1,
              None
            )
            .evalMap(msg => authRemovalEvents.update(_ ++ msg.payload))
            .compile
            .drain
            .start

        _ <- logger.info("Waiting for test documents to be inserted")
        _ <- solrDoc.waitUntil(_.nonEmpty)
        _ <- logger.info("Test documents inserted")

        _ <- queueClient.enqueue(
          queueConfig.userRemoved,
          EventsGenerators.eventMessageGen(Gen.const(UserRemoved(user.id))).generateOne
        )

        _ <- solrDoc.waitUntil(_.isEmpty)
        _ <- logger.info(
          "User has been removed. Waiting for project auth removals"
        )

        expectedAuthRemovals =
          affectedProjects
            .map(ap => ProjectMemberRemoved(ap.id, user.id))
            .toSet

        _ <- authRemovalEvents.waitUntil(x => expectedAuthRemovals.diff(x).isEmpty)

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
        _ <- eventsCollectorFiber.cancel
      yield ()
    }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
