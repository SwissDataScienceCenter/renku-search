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

package io.renku.search.cli.perftests

import cats.effect.{Async, Ref, Resource}
import cats.syntax.all.*
import fs2.Pipe
import io.renku.avro.codec.AvroEncoder
import io.renku.queue.client.QueueClient
import io.renku.redis.client.QueueName
import io.renku.redis.client.ClientId

private trait Enqueuer[F[_]]:
  def enqueue: Pipe[F, QueueDelivery, Unit]

private object Enqueuer:
  def make[F[_]: Async](dryRun: DryRun, clientId: ClientId): Resource[F, Enqueuer[F]] =
    dryRun match {
      case DryRun.Yes =>
        Resource.pure(new DryRunEnqueuer[F])
      case DryRun.No(redisConfig) =>
        QueueClient.make(redisConfig, clientId).map(new RedisEnqueuer(_))
    }

private class RedisEnqueuer[F[_]: Async](qc: QueueClient[F]) extends Enqueuer[F]:

  private val dryRun = DryRunEnqueuer[F]

  override def enqueue: Pipe[F, QueueDelivery, Unit] =
    _.evalTap { delivery =>
      given AvroEncoder[delivery.P] = delivery.encoder
      qc.enqueue(delivery.queue, delivery.message).void
    }.through(dryRun.enqueue)

private class DryRunEnqueuer[F[_]: Async] extends Enqueuer[F]:

  private val cnt: Ref[F, Map[QueueName, Long]] = Ref.unsafe(Map.empty)

  override def enqueue: Pipe[F, QueueDelivery, Unit] =
    _.evalTap { delivery =>
      cnt.update(m => m.updatedWith(delivery.queue)(_.fold(1L.some)(v => (v + 1L).some)))
    }.evalMap { delivery =>
      cnt.get.map { m =>
        val cnt = m.getOrElse(delivery.queue, 1L)
        println(show"Event $cnt for $delivery")
      }
    }
