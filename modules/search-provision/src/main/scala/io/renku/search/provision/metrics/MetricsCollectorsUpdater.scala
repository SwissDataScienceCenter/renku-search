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

import cats.MonadThrow
import cats.effect.Resource
import cats.syntax.all.*
import io.renku.redis.client.{ClientId, RedisQueueClient}
import io.renku.search.metrics.CollectorRegistryBuilder
import io.renku.search.provision.QueuesConfig

object MetricsCollectorsUpdater:

  def make[F[_]: MonadThrow](
      clientId: ClientId,
      registryBuilder: CollectorRegistryBuilder[F],
      queuesConfig: QueuesConfig,
      rcR: Resource[F, RedisQueueClient[F]]
  ): Resource[F, MetricsCollectorsUpdater[F]] =
    rcR.map { rc =>
      val queueSizeGauge = QueueSizeGauge()
      registryBuilder.add(queueSizeGauge)

      val unprocessedGauge = UnprocessedCountGauge()
      registryBuilder.add(unprocessedGauge)

      MetricsCollectorsUpdater[F](
        queuesConfig,
        List(
          new QueueSizeGaugeUpdater[F](rc, queueSizeGauge),
          new UnprocessedCountGaugeUpdater[F](clientId, rc, unprocessedGauge)
        )
      )
    }

class MetricsCollectorsUpdater[F[_]: MonadThrow](
    queuesConfig: QueuesConfig,
    collectors: List[CollectorUpdater[F]]
):

  private val allQueues = queuesConfig.all.toList

  def run(): F[Unit] =
    allQueues.traverse_ { q =>
      collectors.traverse_(_.update(q))
    }
