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

import cats.Monad
import cats.syntax.all.*
import io.renku.queue.client.QueueClient
import io.renku.redis.client.{ClientId, QueueName}

private class UnprocessedCountGaugeUpdater[F[_]: Monad](
    clientId: ClientId,
    rc: QueueClient[F],
    gauge: UnprocessedCountGauge
) extends CollectorUpdater[F]:

  override def update(queueName: QueueName): F[Unit] =
    rc
      .findLastProcessed(clientId, queueName)
      .flatMap {
        case None     => rc.getSize(queueName)
        case Some(lm) => rc.getSize(queueName, lm)
      }
      .map(s => gauge.set(queueName, s.toDouble))
