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

package io.renku.queue.client

import cats.effect.{Async, Resource}
import fs2.Stream
import io.renku.redis.client.{RedisConfig, RedisQueueClient}
import scodec.bits.ByteVector

trait QueueClient[F[_]] {

  def enqueue(
      queueName: QueueName,
      header: Header,
      payload: ByteVector
  ): F[MessageId]

  def acquireEventsStream(
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, Message]

  def markProcessed(
      clientId: ClientId,
      queueName: QueueName,
      messageId: MessageId
  ): F[Unit]

  def findLastProcessed(clientId: ClientId, queueName: QueueName): F[Option[MessageId]]
}

object QueueClient:
  def make[F[_]: Async](redisConfig: RedisConfig): Resource[F, QueueClient[F]] =
    RedisQueueClient.make[F](redisConfig)
