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

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.effect.MkRedis.forAsync
import dev.profunktor.redis4cats.streams.RedisStream
import dev.profunktor.redis4cats.streams.data.{StreamingOffset, XAddMessage, XReadMessage}
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import fs2.Stream
import io.renku.queue.client.*
import scodec.bits.ByteVector
import scribe.Scribe

object RedisQueueClient:
  def apply[F[_]: Async: Scribe]: Resource[F, QueueClient[F]] =
    given Log[F] = RedisLogger[F]
    RedisClient[F].from("url").map(new RedisQueueClient[F](_))

class RedisQueueClient[F[_]: Async: Log](client: RedisClient) extends QueueClient[F] {

  private val payloadKey = "payload"

  override def enqueue(queueName: QueueName, message: ByteVector): F[MessageId] =
    val m = Stream
      .emit[F, XAddMessage[String, ByteVector]](
        XAddMessage(queueName.toString, Map(payloadKey -> message))
      )
    createConnection
      .flatMap(_.append(m))
      .map(id => MessageId(id.value))
      .compile
      .toList
      .map(_.head)

  override def acquireEventsStream(
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, Message] =
    val initialOffset: String => StreamingOffset[String] =
      maybeOffset
        .map(id => StreamingOffset.Custom[String](_, id.value))
        .getOrElse(StreamingOffset.All[String])

    createConnection >>= {
      _.read(Set(queueName.toString), chunkSize, initialOffset)
        .map(toMessage)
        .collect { case Some(m) => m }
    }

  private def toMessage(m: XReadMessage[String, ByteVector]): Option[Message] =
    m.body
      .get(payloadKey)
      .map(Message(MessageId(m.id.value), _))

  private def createConnection =
    RedisStream
      .mkStreamingConnection[F, String, ByteVector](client, StringBytesCodec.instance)

  override def markProcessed(
      clientId: ClientId,
      queueName: QueueName,
      messageId: MessageId
  ): F[Unit] =
    stringCommands.use {
      _.set(formProcessedKey(clientId, queueName), messageId.value)
    }

  override def findLastProcessed(
      clientId: ClientId,
      queueName: QueueName
  ): F[Option[MessageId]] =
    stringCommands.use {
      _.get(formProcessedKey(clientId, queueName)).map(_.map(MessageId.apply))
    }

  private def stringCommands: Resource[F, RedisCommands[F, String, String]] =
    Redis[F].fromClient(client, RedisCodec.Utf8)

  private def formProcessedKey(clientId: ClientId, queueName: QueueName) =
    s"$queueName.$clientId"
}
