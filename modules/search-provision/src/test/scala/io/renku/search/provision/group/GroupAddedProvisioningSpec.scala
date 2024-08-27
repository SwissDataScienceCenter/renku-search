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
import cats.effect.IO

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.GroupAdded
import io.renku.search.model.ModelGenerators
import io.renku.search.model.{Id, Name, Namespace}
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.documents.{CompoundId, EntityDocument, Group as GroupDocument}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class GroupAddedProvisioningSpec extends ProvisioningSuite:

  test("overwrite data for duplicate events"):
    for
      services <- IO(testServices())
      handler = services.syncHandler(queueConfig.groupAdded)
      queueClient = services.queueClient
      solrClient = services.searchClient

      id <- IO(ModelGenerators.idGen.generateOne)
      _ <- solrClient.deleteIds(NonEmptyList.of(id))
      add1 <- queueClient.enqueue(
        queueConfig.groupAdded,
        EventsGenerators
          .eventMessageGen(
            Gen.const(GroupAdded(id, Name("SDSC"), Namespace("sdsc-namespace"), None))
          )
          .generateOne
      )
      add2 <- queueClient.enqueue(
        queueConfig.groupAdded,
        EventsGenerators
          .eventMessageGen(
            Gen.const(
              GroupAdded(id, Name("Renku"), Namespace("sdsc-namespace"), None)
            )
          )
          .generateOne
      )
      results <- handler.create
        .take(2)
        .map(_.asUpsert)
        .unNone
        .compile
        .toList

      _ = assert(results.nonEmpty && results.forall(_.isSuccess))
      doc <- solrClient.findById[EntityDocument](CompoundId.groupEntity(id))
      _ = assert(doc.isDefined, "group not found")
      group = doc.get.asInstanceOf[GroupDocument]
      _ = assertEquals(group.name, Name("Renku"))
    yield ()

  test("can fetch events, decode them, and send them to Solr"):
    val groupAdded = EventsGenerators.groupAddedGen(prefix = "group-added").generateOne
    for
      services <- IO(testServices())
      handler = services.syncHandler(queueConfig.groupAdded)
      queueClient = services.queueClient
      solrClient = services.searchClient

      _ <- queueClient.enqueue(
        queueConfig.groupAdded,
        EventsGenerators.eventMessageGen(Gen.const(groupAdded)).generateOne
      )

      result <- handler.create
        .take(10)
        .map(_.asUpsert)
        .unNone
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
