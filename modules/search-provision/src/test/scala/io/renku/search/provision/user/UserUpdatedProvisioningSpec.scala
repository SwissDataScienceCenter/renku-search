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

import io.renku.events.{v1, v2}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.*
import io.renku.search.provision.events.syntax.*
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.documents.{CompoundId, EntityDocument}
import io.renku.solr.client.DocVersion
import io.renku.search.provision.BackgroundCollector
import io.renku.events.EventsGenerators
import org.scalacheck.Gen

class UserUpdatedProvisioningSpec extends ProvisioningSuite:
  (firstNameUpdate :: lastNameUpdate :: emailUpdate :: noUpdate :: Nil).foreach {
    case TestCase(name, updateF) =>
      val userAdded = EventsGenerators.userAddedGen(prefix = "user-update").generateOne
      test(s"can fetch events, decode them, and update in Solr in case of $name"):
        withMessageHandlers(queueConfig).use { case (handler, queueClient, solrClient) =>
          for
            collector <- BackgroundCollector[EntityDocument](
              solrClient
                .findById[EntityDocument](
                  CompoundId.userEntity(userAdded.id)
                )
                .map(_.toSet)
            )
            _ <- collector.start

            provisioningFiber <- handler.userUpdated.compile.drain.start

            orig = userAdded.toModel(DocVersion.Off)
            _ <- solrClient.upsert(Seq(orig.widen))

            userUpdated = updateF(userAdded)
            _ <- queueClient.enqueue(
              queueConfig.userUpdated,
              EventsGenerators.eventMessageGen(Gen.const(userUpdated)).generateOne
            )

            _ <- collector.waitUntil(docs =>
              docs.map(_.setVersion(DocVersion.Off)) contains userUpdated
                .toModel(orig)
                .setVersion(DocVersion.Off)
            )

            _ <- provisioningFiber.cancel
          yield ()
        }
  }

  private case class TestCase(name: String, f: UserAdded => UserUpdated)
  private lazy val firstNameUpdate = TestCase(
    "firstName update",
    {
      case UserAdded.V1(ua) =>
        UserUpdated.V1(
          v1.UserUpdated(
            ua.id,
            EventsGenerators.stringGen(max = 5).generateOne.some,
            ua.lastName,
            ua.email
          )
        )
      case UserAdded.V2(ua) =>
        UserUpdated.V2(
          v2.UserUpdated(
            ua.id,
            EventsGenerators.stringGen(max = 5).generateOne.some,
            ua.lastName,
            ua.email,
            ua.namespace
          )
        )
    }
  )
  private lazy val lastNameUpdate = TestCase(
    "lastName update",
    {
      case UserAdded.V1(ua) =>
        UserUpdated.V1(
          v1.UserUpdated(
            ua.id,
            ua.firstName,
            EventsGenerators.stringGen(max = 5).generateOne.some,
            ua.email
          )
        )
      case UserAdded.V2(ua) =>
        UserUpdated.V2(
          v2.UserUpdated(
            ua.id,
            ua.firstName,
            EventsGenerators.stringGen(max = 5).generateOne.some,
            ua.email,
            ua.namespace
          )
        )
    }
  )
  private lazy val emailUpdate = TestCase(
    "email update",
    {
      case UserAdded.V1(ua) =>
        UserUpdated.V1(
          v1.UserUpdated(
            ua.id,
            ua.firstName,
            ua.lastName,
            EventsGenerators.stringGen(max = 5).map(v => s"v@host.com").generateOne.some
          )
        )
      case UserAdded.V2(ua) =>
        UserUpdated.V2(
          v2.UserUpdated(
            ua.id,
            ua.firstName,
            ua.lastName,
            EventsGenerators.stringGen(max = 5).map(v => s"v@host.com").generateOne.some,
            ua.namespace
          )
        )
    }
  )
  private lazy val noUpdate = TestCase(
    "no update",
    {
      case UserAdded.V1(ua) =>
        UserUpdated.V1(v1.UserUpdated(ua.id, ua.firstName, ua.lastName, ua.email))
      case UserAdded.V2(ua) =>
        UserUpdated.V2(
          v2.UserUpdated(ua.id, ua.firstName, ua.lastName, ua.email, ua.namespace)
        )
    }
  )

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
