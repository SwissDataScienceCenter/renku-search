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
import io.renku.avro.codec.AvroIO
import io.renku.avro.codec.decoders.all.given
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.*
import io.renku.events.v1.*
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.QueueSpec
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.{QueueName, RedisClientGenerators}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.EntityType
import io.renku.search.model.ModelGenerators.projectMemberRoleGen
import io.renku.search.provision.QueueMessageDecoder
import io.renku.search.provision.project.ProjectSyntax
import io.renku.search.query.Query
import io.renku.search.query.Query.Segment
import io.renku.search.query.Query.Segment.typeIs
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.{Entity, User}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

class UserRemovedProcessSpec
    extends CatsEffectSuite
    with QueueSpec
    with SearchSolrSpec
    with ProjectSyntax:

  private val avro = AvroIO(UserRemoved.SCHEMA$)

  test(
    "can fetch events, decode them, and remove from Solr relevant User document " +
      "and issue ProjectAuthorizationRemoved events for all affected projects"
  ):
    val userRemovedQueue = RedisClientGenerators.queueNameGen.generateOne
    val authRemovedQueue = RedisClientGenerators.queueNameGen.generateOne
    val messageDecoder = QueueMessageDecoder[IO, ProjectAuthorizationRemoved](
      ProjectAuthorizationRemoved.SCHEMA$
    )

    clientsAndProvisioning(userRemovedQueue, authRemovedQueue).use {
      case (queueClient, solrClient, provisioner) =>
        for
          solrDoc <- SignallingRef.of[IO, Option[Entity]](None)
          authRemovalEvents <- SignallingRef.of[IO, Set[ProjectAuthorizationRemoved]](
            Set.empty
          )

          provisioningFiber <- provisioner.removalProcess.start

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
              .evalMap(_ => solrClient.findById[User](user.id.value))
              .evalMap(e => solrDoc.update(_ => e))
              .compile
              .drain
              .start

          _ <- solrDoc.waitUntil(_.nonEmpty)

          eventsCollectorFiber <-
            queueClient
              .acquireEventsStream(authRemovedQueue, 1, None)
              .evalMap(messageDecoder.decodeMessage)
              .evalMap(e => authRemovalEvents.update(_ ++ e))
              .compile
              .drain
              .start

          _ <- queueClient.enqueue(
            userRemovedQueue,
            messageHeaderGen(UserRemoved.SCHEMA$).generateOne,
            UserRemoved(user.id.value)
          )

          _ <- solrDoc.waitUntil(_.isEmpty)

          _ <- authRemovalEvents.waitUntil(
            _ == affectedProjects
              .map(ap => ProjectAuthorizationRemoved(ap.id.value, user.id.value))
              .toSet
          )

          _ <- provisioningFiber.cancel
          _ <- docsCollectorFiber.cancel
          _ <- eventsCollectorFiber.cancel
        yield ()
    }

  private lazy val queryProjects = Query(typeIs(EntityType.Project))

  private def clientsAndProvisioning(
      userRemovedQueue: QueueName,
      authRemovedQueue: QueueName
  ) =
    (withQueueClient() >>= withSearchSolrClient().tupleLeft)
      .flatMap { case (rc, sc) =>
        UserRemovedProcess
          .make[IO](
            userRemovedQueue,
            authRemovedQueue,
            withRedisClient.redisConfig,
            withSearchSolrClient.solrConfig
          )
          .map((rc, sc, _))
      }

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
