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

import cats.data.NonEmptyList
import cats.effect.IO

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.UserAdded
import io.renku.search.model.Id
import io.renku.search.model.ModelGenerators
import io.renku.search.model.users.FirstName
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.documents.{CompoundId, EntityDocument, User as UserDocument}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class UserAddedProvisioningSpec extends ProvisioningSuite:
  test("overwrite data for duplicate events"):
    for
      services <- IO(testServices())
      handler = services.messageHandlers
      queueClient = services.queueClient
      solrClient = services.searchClient

      id <- IO(ModelGenerators.idGen.generateOne)
      _ <- solrClient.deleteIds(NonEmptyList.of(id))
      add1 <- queueClient.enqueue(
        queueConfig.userAdded,
        EventsGenerators
          .eventMessageGen(
            EventsGenerators
              .userAddedGen("ua-", Gen.const(FirstName("john1")))
              .map(_.withId(id))
          )
          .generateOne
      )
      results1 <- handler
        .makeUpsert[UserAdded](queueConfig.userAdded)
        .take(1)
        .compile
        .toList

      add2 <- queueClient.enqueue(
        queueConfig.userAdded,
        EventsGenerators
          .eventMessageGen(
            EventsGenerators
              .userAddedGen("ua-", Gen.const(FirstName("john2")))
              .map(_.withId(id))
          )
          .generateOne
      )
      results2 <- handler
        .makeUpsert[UserAdded](queueConfig.userAdded)
        .take(1)
        .compile
        .toList
      results = results1 ++ results2

      _ = assert(results.nonEmpty && results.forall(_.isSuccess))
      doc <- solrClient.findById[EntityDocument](CompoundId.userEntity(id))
      _ = assert(doc.isDefined, "user not found")
      user = doc.get.asInstanceOf[UserDocument]
      _ = assertEquals(user.firstName, Some(FirstName("john2")))
    yield ()

  test("can fetch events, decode them, and send them to Solr"):
    val userAdded = EventsGenerators.userAddedGen(prefix = "user-added").generateOne
    for
      services <- IO(testServices())
      handler = services.messageHandlers
      queueClient = services.queueClient
      solrClient = services.searchClient

      _ <- queueClient.enqueue(
        queueConfig.userAdded,
        EventsGenerators.eventMessageGen(Gen.const(userAdded)).generateOne
      )

      result <- handler
        .makeUpsert[UserAdded](queueConfig.userAdded)
        .take(10)
        .find(_.isSuccess)
        .compile
        .lastOrError

      _ = assert(result.isSuccess)

      doc <- solrClient.findById[EntityDocument](
        CompoundId.userEntity(userAdded.id)
      )
      _ = assert(doc.isDefined)
      _ = assertEquals(
        doc.get.setVersion(DocVersion.Off),
        userAdded.toModel(DocVersion.Off)
      )
    yield ()
