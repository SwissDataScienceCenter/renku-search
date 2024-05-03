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

package io.renku.search.provision.metrics

import cats.NonEmptyParallel
import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream
import io.renku.queue.client.QueueClient
import io.renku.redis.client.ClientId
import io.renku.search.config.QueuesConfig
import io.renku.search.solr.client.SearchSolrClient

import scala.concurrent.duration.FiniteDuration

object MetricsCollectorsUpdater:
  def apply[F[_]: Async: NonEmptyParallel](
      clientId: ClientId,
      queuesConfig: QueuesConfig,
      updateInterval: FiniteDuration,
      queueClient: Stream[F, QueueClient[F]],
      solrClient: SearchSolrClient[F]
  ): MetricsCollectorsUpdater[F] =
    new MetricsCollectorsUpdater[F](
      RedisMetricsCollectorsUpdater[F](
        queuesConfig,
        updateInterval,
        queueClient
      ),
      SolrMetricsCollectorsUpdater[F](updateInterval, solrClient)
    )

class MetricsCollectorsUpdater[F[_]: Async: NonEmptyParallel](
    rcu: RedisMetricsCollectorsUpdater[F],
    scu: SolrMetricsCollectorsUpdater[F]
):
  def run(): F[Unit] =
    (rcu.run(), scu.run()).parTupled.void
