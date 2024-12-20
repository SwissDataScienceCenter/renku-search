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

import cats.effect.*

import io.renku.queue.client.QueueClient
import io.renku.redis.client.QueueName
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.provision.reindex.ReIndexService
import io.renku.search.provision.reindex.ReprovisionService
import io.renku.search.solr.client.SearchSolrClient

final case class TestServices(
    pipelineSteps: QueueName => PipelineSteps[IO],
    messageHandlers: MessageHandlers[IO],
    queueClient: QueueClient[IO],
    searchClient: SearchSolrClient[IO],
    backgroundManage: BackgroundProcessManage[IO],
    reindex: ReIndexService[IO]
):
  val reprovision: ReprovisionService[IO] =
    ReprovisionService(reindex, searchClient.underlying)

  def syncHandler(qn: QueueName): SyncMessageHandler[IO] =
    messageHandlers.createHandler(qn)
