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
import cats.effect.{IO, Resource}

import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.userAddedGen
import io.renku.events.v1.UserAdded
import io.renku.queue.client.DataContentType
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.model.users.FirstName
import io.renku.search.provision.events.syntax.*
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.handler.ShowInstances
import io.renku.search.solr.documents.{CompoundId, EntityDocument, User as UserDocument}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class UserAddedProvisioningSpec extends ProvisioningSuite with ShowInstances:
  test("overwrite data for duplicate events"):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        id <- IO(Gen.uuid.map(uid => Id(uid.toString)).generateOne)
        _ <- solrClient.deleteIds(NonEmptyList.of(id))
        add1 <- queueClient.enqueue(
          queueConfig.userAdded,
          messageHeaderGen(UserAdded.SCHEMA$, DataContentType.Binary).generateOne,
          UserAdded(id.value, Some("john1"), None, None)
        )
        add2 <- queueClient.enqueue(
          queueConfig.userAdded,
          messageHeaderGen(UserAdded.SCHEMA$, DataContentType.Binary).generateOne,
          UserAdded(id.value, Some("john2"), None, None)
        )
        results <- handlers
          .makeUpsert[UserAdded](queueConfig.userAdded)
          .take(2)
          .compile
          .toList

        _ = assert(results.nonEmpty && results.forall(_.isSuccess))
        doc <- solrClient.findById[EntityDocument](CompoundId.userEntity(id))
        _ = assert(doc.isDefined, "user not found")
        user = doc.get.asInstanceOf[UserDocument]
        _ = assertEquals(user.firstName, Some(FirstName("john2")))
      yield ()
    }

  test("can fetch events, decode them, and send them to Solr"):
    val userAdded = userAddedGen(prefix = "user-added").generateOne
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        _ <- queueClient.enqueue(
          queueConfig.userAdded,
          messageHeaderGen(UserAdded.SCHEMA$).generateOne,
          userAdded
        )

        result <- handlers
          .makeUpsert[UserAdded](queueConfig.userAdded)
          .take(10)
          .find(_.isSuccess)
          .compile
          .lastOrError

        _ = assert(result.isSuccess)

        doc <- solrClient.findById[EntityDocument](
          CompoundId.userEntity(Id(userAdded.id))
        )
        _ = assert(doc.isDefined)
        _ = assertEquals(
          doc.get.setVersion(DocVersion.Off),
          userAdded.toModel(DocVersion.Off)
        )
      yield ()
    }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
