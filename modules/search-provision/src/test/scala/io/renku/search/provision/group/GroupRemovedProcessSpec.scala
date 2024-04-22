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

package io.renku.search.provision.group

import cats.effect.{IO, Resource}
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.*
import io.renku.events.v2.GroupRemoved
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.SchemaVersion
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.documents.{CompoundId, EntityDocument}
import io.renku.solr.client.DocVersion

import scala.concurrent.duration.*

class GroupRemovedProcessSpec extends ProvisioningSuite:

  test("can fetch events, decode them, and remove the Group from Solr"):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        solrDoc <- SignallingRef.of[IO, Option[EntityDocument]](None)

        provisioningFiber <- handlers.groupRemoved.compile.drain.start

        added = groupAddedGen(prefix = "group-removal").generateOne
        _ <- solrClient.upsert(Seq(added.fold(_.toModel(DocVersion.Off).widen)))

        docsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ =>
              solrClient.findById[EntityDocument](CompoundId.groupEntity(added.id))
            )
            .evalMap(e => solrDoc.update(_ => e))
            .compile
            .drain
            .start

        _ <- solrDoc.waitUntil(_.nonEmpty)

        removed = GroupRemoved(added.id.value)
        _ <- queueClient.enqueue(
          queueConfig.groupRemoved,
          messageHeaderGen(GroupRemoved.SCHEMA$, SchemaVersion.V2).generateOne,
          removed
        )

        _ <- solrDoc.waitUntil(_.isEmpty)

        _ <- provisioningFiber.cancel
        _ <- docsCollectorFiber.cancel
      yield ()
    }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
