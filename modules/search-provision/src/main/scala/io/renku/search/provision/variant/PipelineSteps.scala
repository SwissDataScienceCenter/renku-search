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

package io.renku.search.provision.variant

import cats.effect.{Resource, Sync}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.queue.client.QueueClient
import io.renku.redis.client.ClientId
import io.renku.redis.client.QueueName
import io.renku.search.provision.QueuesConfig

trait PipelineSteps[F[_]]:
  def reader: MessageReader[F]
  def converter: ConvertDocument[F]
  def pushToSolr: PushToSolr[F]
  def fetchFromSolr: FetchFromSolr[F]
  def deleteFromSolr: DeleteFromSolr[F]
  def pushToRedis: PushToRedis[F]
  def userUtils: UserUtils[F]

object PipelineSteps:
  def apply[F[_]: Sync](
      solrClient: SearchSolrClient[F],
      queueClient: Resource[F, QueueClient[F]],
      queueConfig: QueuesConfig,
      inChunkSize: Int,
      clientId: ClientId
  )(
      queue: QueueName
  ): PipelineSteps[F] =
    new PipelineSteps[F] {
      val reader: MessageReader[F] =
        MessageReader[F](queueClient, queue, clientId, inChunkSize)
      val converter: ConvertDocument[F] =
        ConvertDocument[F]
      val pushToSolr: PushToSolr[F] =
        PushToSolr[F](solrClient, queueClient, clientId, queue)
      val fetchFromSolr: FetchFromSolr[F] =
        FetchFromSolr[F](solrClient)
      val deleteFromSolr: DeleteFromSolr[F] =
        DeleteFromSolr[F](solrClient, queueClient, clientId, queue)
      val pushToRedis: PushToRedis[F] =
        PushToRedis[F](queueClient, clientId, queueConfig)

      val userUtils: UserUtils[F] =
        UserUtils[F](fetchFromSolr, pushToRedis)
    }
