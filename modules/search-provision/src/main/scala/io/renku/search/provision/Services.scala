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

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import fs2.io.net.Network

import io.renku.queue.client.QueueClient
import io.renku.redis.client.ClientId
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.solr.client.SearchSolrClient

final case class Services[F[_]](
    config: SearchProvisionConfig,
    solrClient: SearchSolrClient[F],
    queueClient: Resource[F, QueueClient[F]],
    messageHandlers: MessageHandlers[F]
)

object Services:
  val clientId: ClientId = ClientId("search-provisioner")
  def make[F[_]: Async: Network]: Resource[F, Services[F]] =
    for {
      cfg <- Resource.eval(SearchProvisionConfig.config.load[F])
      solr <- SearchSolrClient.make[F](cfg.solrConfig)

      // The redis client must be initialized on each operation to
      // be able to connect to the cluster
      redis = QueueClient.make[F](cfg.redisConfig)

      steps = PipelineSteps[F](solr, redis, cfg.queuesConfig, 1, clientId)
      handlers = MessageHandlers[F](steps, cfg.queuesConfig)
    } yield Services(cfg, solr, redis, handlers)
