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

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.Stream
import io.renku.queue.client.QueueClient
import io.renku.redis.client.ClientId
import io.renku.search.metrics.CollectorRegistryBuilder
import io.renku.search.provision.QueuesConfig

import scala.concurrent.duration.FiniteDuration

object MetricsCollectorsUpdater:

  def apply[F[_]: Async](
      clientId: ClientId,
      registryBuilder: CollectorRegistryBuilder[F],
      queuesConfig: QueuesConfig,
      updateInterval: FiniteDuration,
      qcResource: Resource[F, QueueClient[F]]
  ): MetricsCollectorsUpdater[F] =

    val queueSizeGauge = QueueSizeGauge()
    registryBuilder.add(queueSizeGauge)

    val unprocessedGauge = UnprocessedCountGauge()
    registryBuilder.add(unprocessedGauge)

    new MetricsCollectorsUpdater[F](
      qcResource,
      queuesConfig,
      List(
        new QueueSizeGaugeUpdater[F](_, queueSizeGauge),
        new UnprocessedCountGaugeUpdater[F](clientId, _, unprocessedGauge)
      ),
      updateInterval
    )

class MetricsCollectorsUpdater[F[_]: Async](
    qcResource: Resource[F, QueueClient[F]],
    queuesConfig: QueuesConfig,
    collectors: List[QueueClient[F] => CollectorUpdater[F]],
    updateInterval: FiniteDuration
):

  private val allQueues = queuesConfig.all.toList

  def run(): F[Unit] =
    val queueClient: Stream[F, QueueClient[F]] =
      Stream.resource(qcResource)
    val awake: Stream[F, Unit] =
      Stream.awakeEvery[F](updateInterval).void
    queueClient
      .flatTap(_ => awake)
      .evalMap(runUpdate)
      .compile
      .drain

  private def runUpdate(qc: QueueClient[F]) =
    allQueues.traverse_ { q =>
      collectors
        .map(_.apply(qc))
        .traverse_(_.update(q))
    }