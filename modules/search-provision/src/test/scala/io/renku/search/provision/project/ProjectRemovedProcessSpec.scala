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

import scala.concurrent.duration.*

import cats.effect.{IO, Resource}
import fs2.Stream
import fs2.concurrent.SignallingRef

import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.*
import io.renku.events.v1.ProjectRemoved
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.{EntityType, Id}
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.query.Query
import io.renku.search.query.Query.Segment
import io.renku.search.query.Query.Segment.typeIs
import io.renku.search.solr.documents.{CompoundId, EntityDocument}

class ProjectRemovedProcessSpec extends ProvisioningSuite:

  test(s"can fetch events, decode them, and remove Solr"):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        solrDoc <- SignallingRef.of[IO, Option[EntityDocument]](None)

        provisioningFiber <- handlers.projectRemoved.compile.drain.start

        created = projectCreatedGen(prefix = "remove").generateOne
        _ <- solrClient.insert(Seq(created.toSolrDocument.widen))

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ =>
              solrClient.findById[EntityDocument](
                CompoundId.projectEntity(Id(created.id))
              )
            )
            .evalMap(e => solrDoc.update(_ => e))
            .compile
            .drain
            .start

        _ <- solrDoc.waitUntil(
          _.nonEmpty
        )

        removed = ProjectRemoved(created.id)
        _ <- queueClient.enqueue(
          queueConfig.projectRemoved,
          messageHeaderGen(ProjectRemoved.SCHEMA$).generateOne,
          removed
        )

        _ <- solrDoc.waitUntil(
          _.isEmpty
        )

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  private lazy val queryProjects = Query(typeIs(EntityType.Project))

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
