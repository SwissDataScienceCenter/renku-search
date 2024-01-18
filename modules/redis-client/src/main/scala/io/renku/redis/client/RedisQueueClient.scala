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

package io.renku.redis.client

import cats.effect.Async
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.streams.RedisStream
import dev.profunktor.redis4cats.streams.data.XAddMessage
import fs2.Stream
import io.renku.queue.client.{QueueClient, QueueName}

class RedisQueueClient[F[_]: Async: Log](client: RedisClient) extends QueueClient[F] {

  private val payloadKey = "payload"

  override def enqueue(queueName: QueueName, message: String): F[Unit] =
    val m = Stream
      .emit[F, XAddMessage[String, String]](
        XAddMessage(queueName.value, Map(payloadKey -> message))
      )
    createConnection.flatMap(_.append(m)).compile.drain

  override def acquireEventsStream(queueName: QueueName): Stream[F, String] =
    createConnection >>= {
      _.read(Set(queueName.value), chunkSize = 1)
        .map(_.body.get(payloadKey))
        .collect { case Some(m) => m }
    }

  private def createConnection =
    RedisStream.mkStreamingConnection[F, String, String](client, RedisCodec.Utf8)
}
