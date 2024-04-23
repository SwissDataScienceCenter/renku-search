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
import io.renku.avro.codec.AvroEncoder
import io.renku.search.events.{EventMessage, RenkuEventPayload, SchemaVersion}
import io.renku.redis.client.*
import io.renku.avro.codec.AvroDecoder

trait QueueClient[F[_]]:

  def enqueue[P: AvroEncoder](
      queueName: QueueName,
      header: MessageHeader,
      payload: P
  ): F[MessageId]

  def enqueue[P <: RenkuEventPayload: AvroEncoder](
      queueName: QueueName,
      schemaVersion: SchemaVersion,
      msg: EventMessage[P]
  ): F[MessageId]

  def acquireEventsStream(
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, QueueMessage]

  def acquireHeaderEventsStream(
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, QueueHeaderMessage]

  def acquireMessageStream[T <: RenkuEventPayload](
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId],
      schemaSelect: SchemaSelect
  )(using AvroDecoder[T]): Stream[F, EventMessage[T]]

  def markProcessed(
      clientId: ClientId,
      queueName: QueueName,
      messageId: MessageId
  ): F[Unit]

  def findLastProcessed(clientId: ClientId, queueName: QueueName): F[Option[MessageId]]

  def getSize(queueName: QueueName): F[Long]

  def getSize(queueName: QueueName, from: MessageId): F[Long]

object QueueClient:

  // Be aware that it was observed that the client can lose the connection to Redis.
  // Because of that consider using the QueueClient.stream
  // that auto-refreshes (recreates) the connection every connectionRefreshInterval.
  def make[F[_]: Async](redisConfig: RedisConfig): Resource[F, QueueClient[F]] =
    RedisQueueClient.make[F](redisConfig).map(new QueueClientImpl[F](_))

  def stream[F[_]: Async](redisConfig: RedisConfig): Stream[F, QueueClient[F]] =
    val s = Stream
      .resource[F, QueueClient[F]](make(redisConfig))
      .interruptAfter(redisConfig.connectionRefreshInterval)
    s ++ stream(redisConfig)
