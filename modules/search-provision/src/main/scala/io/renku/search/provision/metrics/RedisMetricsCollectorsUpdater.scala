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

import scala.concurrent.duration.FiniteDuration

import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream

import io.renku.queue.client.QueueClient
import io.renku.search.config.QueuesConfig

private object RedisMetricsCollectorsUpdater:

  def apply[F[_]: Async](
      queuesConfig: QueuesConfig,
      updateInterval: FiniteDuration,
      queueClient: Stream[F, QueueClient[F]]
  ): RedisMetricsCollectorsUpdater[F] =
    new RedisMetricsCollectorsUpdater[F](
      queueClient,
      queuesConfig,
      RedisMetrics.updaterFactories,
      updateInterval
    )

private class RedisMetricsCollectorsUpdater[F[_]: Async](
    queueClient: Stream[F, QueueClient[F]],
    queuesConfig: QueuesConfig,
    collectors: List[QueueClient[F] => CollectorUpdater[F]],
    updateInterval: FiniteDuration
):

  private val allQueues = queuesConfig.all.toList

  def run(): F[Unit] =
    createUpdateStream.compile.drain

  private def createUpdateStream: Stream[F, Unit] =
    val awake: Stream[F, Unit] =
      Stream.awakeEvery[F](updateInterval).void
    queueClient
      .flatTap(_ => awake)
      .evalMap(runUpdate)

  private def runUpdate(qc: QueueClient[F]) =
    allQueues.traverse_ { q =>
      collectors
        .map(_.apply(qc))
        .traverse_(_.update(q))
    }
