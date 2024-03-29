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

package io.renku.search.provision

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream

import io.renku.queue.client.{QueueClient, QueueSpec}
import io.renku.redis.client.{ClientId, QueueName}
import io.renku.search.model.Id
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.provision.project.ProjectSyntax
import io.renku.search.provision.user.UserSyntax
import io.renku.search.solr.client.{SearchSolrClient, SearchSolrSuite}
import io.renku.search.solr.documents.*

trait ProvisioningSuite
    extends SearchSolrSuite
    with QueueSpec
    with ProjectSyntax
    with UserSyntax:

  val queueConfig: QueuesConfig = QueuesConfig(
    projectCreated = QueueName("projectCreated"),
    projectUpdated = QueueName("projectUpdated"),
    projectRemoved = QueueName("projectRemoved"),
    projectAuthorizationAdded = QueueName("projectAuthorizationAdded"),
    projectAuthorizationUpdated = QueueName("projectAuthorizationUpdated"),
    projectAuthorizationRemoved = QueueName("projectAuthorizationRemoved"),
    userAdded = QueueName("userAdded"),
    userUpdated = QueueName("userUpdated"),
    userRemoved = QueueName("userRemoved")
  )

  def withMessageHandlers(
      cfg: QueuesConfig = queueConfig
  ): Resource[IO, (MessageHandlers[IO], QueueClient[IO], SearchSolrClient[IO])] =
    val clientId = ClientId("provision-test-client")
    (withSearchSolrClient(), withQueueClient()).mapN { (solrClient, queueClient) =>
      val steps =
        PipelineSteps[IO](
          solrClient,
          Stream[IO, QueueClient[IO]](queueClient),
          queueConfig,
          inChunkSize = 1,
          clientId
        )
      val handlers = MessageHandlers[IO](steps, queueConfig)
      (handlers, queueClient, solrClient)
    }

  def loadPartialOrEntity(solrClient: SearchSolrClient[IO], id: Id) =
    (
      solrClient.findById[EntityDocument](
        CompoundId.projectEntity(id)
      ),
      solrClient.findById[PartialEntityDocument](
        CompoundId.projectPartial(id)
      )
    ).mapN((a, b) => a.toSet ++ b.toSet)
