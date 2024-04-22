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

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.groupAddedGen
import io.renku.events.v2
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.queue.client.SchemaVersion
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.GroupAdded
import io.renku.search.model.{Id, groups}
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.provision.handler.QueueMessageDecoder.given
import io.renku.search.provision.handler.ShowInstances
import io.renku.search.solr.documents.{CompoundId, EntityDocument, Group as GroupDocument}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class GroupAddedProvisioningSpec extends ProvisioningSuite with ShowInstances:

  test("overwrite data for duplicate events"):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        id <- IO(Gen.uuid.map(uid => Id(uid.toString)).generateOne)
        _ <- solrClient.deleteIds(NonEmptyList.of(id))
        add1 <- queueClient.enqueue(
          queueConfig.groupAdded,
          messageHeaderGen(v2.GroupAdded.SCHEMA$, SchemaVersion.V2).generateOne,
          v2.GroupAdded(id.value, "SDSC", None, "sdsc-namespace")
        )
        add2 <- queueClient.enqueue(
          queueConfig.groupAdded,
          messageHeaderGen(v2.GroupAdded.SCHEMA$, SchemaVersion.V2).generateOne,
          v2.GroupAdded(id.value, "Renku", None, "sdsc-namespace")
        )
        results <- handlers
          .makeUpsert[GroupAdded](queueConfig.groupAdded)
          .take(2)
          .compile
          .toList

        _ = assert(results.nonEmpty && results.forall(_.isSuccess))
        doc <- solrClient.findById[EntityDocument](CompoundId.groupEntity(id))
        _ = assert(doc.isDefined, "group not found")
        group = doc.get.asInstanceOf[GroupDocument]
        _ = assertEquals(group.name, groups.Name("Renku"))
      yield ()
    }

  test("can fetch events, decode them, and send them to Solr"):
    val groupAdded = groupAddedGen(prefix = "group-added").generateOne
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        _ <- queueClient.enqueue(
          queueConfig.groupAdded,
          messageHeaderGen(v2.GroupAdded.SCHEMA$, SchemaVersion.V2).generateOne,
          groupAdded
        )

        result <- handlers
          .makeUpsert[GroupAdded](queueConfig.groupAdded)
          .take(10)
          .find(_.isSuccess)
          .compile
          .lastOrError

        _ = assert(result.isSuccess)

        doc <- solrClient.findById[EntityDocument](
          CompoundId.groupEntity(groupAdded.id)
        )
        _ = assert(doc.isDefined)
        _ = assertEquals(
          doc.get.setVersion(DocVersion.Off),
          groupAdded.fold(_.toModel(DocVersion.Off))
        )
      yield ()
    }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
