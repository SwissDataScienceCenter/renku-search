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

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.github.arainko.ducktape.*
import io.renku.avro.codec.AvroIO
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.*
import io.renku.events.v1.{ProjectCreated, ProjectRemoved}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.QueueSpec
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.{QueueName, RedisClientGenerators}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.{EntityType, projects, users}
import io.renku.search.query.Query
import io.renku.search.query.Query.Segment
import io.renku.search.query.Query.Segment.typeIs
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.{Entity, Project}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

class ProjectRemovedProvisioningSpec
    extends CatsEffectSuite
    with QueueSpec
    with SearchSolrSpec:

  private val avro = AvroIO(ProjectRemoved.SCHEMA$)

  test(s"can fetch events, decode them, and remove Solr"):
    val queue = RedisClientGenerators.queueNameGen.generateOne

    clientsAndProvisioning(queue).use { case (queueClient, solrClient, provisioner) =>
      for
        solrDoc <- SignallingRef.of[IO, Option[Project]](None)

        provisioningFiber <- provisioner.removalProcess.start

        created = projectCreatedGen(prefix = "remove").generateOne
        _ <- solrClient.insert(Seq(toSolrDocument(created).widen))

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ => solrClient.findById[Project](created.id))
            // .flatMap(qr => Stream.emits(qr.responseBody.docs))
            .evalMap(e => solrDoc.update(_ => e))
            .compile
            .drain
            .start

        _ <- solrDoc.waitUntil(
          _.nonEmpty
        )

        removed = ProjectRemoved(created.id)
        _ <- queueClient.enqueue(
          queue,
          messageHeaderGen(ProjectRemoved.SCHEMA$).generateOne,
          removed
        )

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ => solrClient.findById[Project](created.id))
            .evalMap(e => solrDoc.update(_ => e))
            .compile
            .drain
            .start

        _ <- solrDoc.waitUntil(
          _.isEmpty
        )

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  private lazy val queryProjects = Query(typeIs(EntityType.Project))

  private def clientsAndProvisioning(queueName: QueueName) =
    (withQueueClient() >>= withSearchSolrClient().tupleLeft)
      .flatMap { case (rc, sc) =>
        ProjectRemovedProvisioning
          .make[IO](
            queueName,
            withRedisClient.redisConfig,
            withSearchSolrClient.solrConfig
          )
          .map((rc, sc, _))
      }

  private def toSolrDocument(created: ProjectCreated): Project =
    created
      .into[Project]
      .transform(
        Field.computed(
          _.visibility,
          pc => projects.Visibility.unsafeFromString(pc.visibility.name())
        ),
        Field.default(_.score)
      )

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
