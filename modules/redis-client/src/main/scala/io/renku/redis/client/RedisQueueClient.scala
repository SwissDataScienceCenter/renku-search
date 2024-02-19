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

import cats.MonadThrow
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.streams.data.{StreamingOffset, XAddMessage, XReadMessage}
import dev.profunktor.redis4cats.streams.{RedisStream, Streaming}
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import fs2.Stream
import io.renku.queue.client.*
import io.renku.redis.client.RedisMessage.*
import scodec.bits.ByteVector
import scribe.Scribe

object RedisQueueClient:

  def make[F[_]: Async](redisConfig: RedisConfig): Resource[F, QueueClient[F]] =
    given Scribe[F] = scribe.cats[F]
    given Log[F] = RedisLogger[F]
    ClientCreator[F](redisConfig).makeClient.map(new RedisQueueClient(_))

class RedisQueueClient[F[_]: Async: Log](client: RedisClient) extends QueueClient[F] {

  override def enqueue(
      queueName: QueueName,
      header: Header,
      payload: ByteVector
  ): F[MessageId] =
    for
      messageBody <- MonadThrow[F].fromEither(RedisMessage.bodyFrom(header, payload))
      message = Stream.emit[F, XAddMessage[String, ByteVector]](
        XAddMessage(queueName.name, messageBody)
      )
      id <- makeStreamingConnection
        .flatMap(_.append(message))
        .map(id => MessageId(id.value))
        .compile
        .toList
        .map(_.head)
    yield id

  override def acquireEventsStream(
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, Message] =
    val initialOffset: String => StreamingOffset[String] =
      maybeOffset
        .map(id => StreamingOffset.Custom[String](_, id.value))
        .getOrElse(StreamingOffset.All[String])

    def logError(rm: XReadMessage[_, _]): Throwable => F[Option[Message]] = err =>
      Log[F]
        .error(s"Decoding message ${rm.id} failed: ${err.getMessage}")
        .as(Option.empty)

    makeStreamingConnection >>= {
      _.read(Set(queueName.name), chunkSize, initialOffset)
        .evalMap(rm => toMessage(rm).fold(logError(rm), _.pure[F]))
        .collect { case Some(m) => m }
    }

  override def markProcessed(
      clientId: ClientId,
      queueName: QueueName,
      messageId: MessageId
  ): F[Unit] =
    createStringCommands.use {
      _.set(formProcessedKey(clientId, queueName), messageId.value)
    }

  override def findLastProcessed(
      clientId: ClientId,
      queueName: QueueName
  ): F[Option[MessageId]] =
    createStringCommands.use {
      _.get(formProcessedKey(clientId, queueName)).map(_.map(MessageId.apply))
    }

  private def formProcessedKey(clientId: ClientId, queueName: QueueName) =
    s"$queueName.$clientId"

  private def makeStreamingConnection
      : Stream[F, Streaming[[A] =>> Stream[F, A], String, ByteVector]] =
    RedisStream
      .mkStreamingConnection[F, String, ByteVector](client, StringBytesCodec.instance)

  private def createStringCommands: Resource[F, RedisCommands[F, String, String]] =
    Redis[F].fromClient(client, RedisCodec.Utf8)
}
