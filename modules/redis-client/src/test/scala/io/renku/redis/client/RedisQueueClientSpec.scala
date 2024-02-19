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

import cats.effect.IO
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.streams.data.XAddMessage
import dev.profunktor.redis4cats.streams.{RedisStream, Streaming}
import fs2.*
import fs2.concurrent.SignallingRef
import io.renku.queue.client.{DataContentType, MessageId, QueueName}
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.util.RedisSpec
import munit.CatsEffectSuite
import scodec.bits.ByteVector

class RedisQueueClientSpec extends CatsEffectSuite with RedisSpec:

  test("can enqueue and dequeue events"):
    withRedisClient.asQueueClient().use { client =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      for
        dequeued <- SignallingRef.of[IO, List[(String, DataContentType)]](Nil)

        message1 = "message1"
        message1Head = headerGen.generateOne
        _ <- client.enqueue(queue, message1Head, toByteVector(message1))

        streamingProcFiber <- client
          .acquireEventsStream(queue, chunkSize = 1, maybeOffset = None)
          .evalMap(event =>
            dequeued.update(toStringUft8(event.payload) -> event.contentType :: _)
          )
          .compile
          .drain
          .start
        _ <- dequeued.waitUntil(_ == List(message1 -> message1Head.dataContentType))

        message2 = "message2"
        message2Head = headerGen.generateOne
        _ <- client.enqueue(queue, message2Head, toByteVector(message2))
        _ <- dequeued
          .waitUntil(
            _.toSet == Set(
              message1 -> message1Head.dataContentType,
              message2 -> message2Head.dataContentType
            )
          )

        _ <- streamingProcFiber.cancel
      yield ()
    }

  test("can start enqueueing events from the given messageId excluding"):
    withRedisClient.asQueueClient().use { client =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      for
        dequeued <- SignallingRef.of[IO, List[String]](Nil)

        message1 = "message1"
        message1Id <- client.enqueue(queue, headerGen.generateOne, toByteVector(message1))

        streamingProcFiber <- client
          .acquireEventsStream(queue, chunkSize = 1, maybeOffset = message1Id.some)
          .evalMap(event => dequeued.update(toStringUft8(event.payload) :: _))
          .compile
          .drain
          .start

        message2 = "message2"
        _ <- client.enqueue(queue, headerGen.generateOne, toByteVector(message2))
        _ <- dequeued.waitUntil(_.toSet == Set(message2))

        message3 = "message3"
        _ <- client.enqueue(queue, headerGen.generateOne, toByteVector(message3))
        _ <- dequeued.waitUntil(_.toSet == Set(message2, message3))
        _ <- streamingProcFiber.cancel
      yield ()
    }

  test("can skip events that fails decoding"):
    withRedisClient().flatMap(rc => withRedisClient.asQueueClient().tupleLeft(rc)).use {
      case (redisClient, queueClient) =>
        val queue = RedisClientGenerators.queueNameGen.generateOne
        for
          dequeued <- SignallingRef.of[IO, List[String]](Nil)

          _ <- enqueue(redisClient, queue, toByteVector("message1"))

          streamingProcFiber <- queueClient
            .acquireEventsStream(queue, chunkSize = 1, maybeOffset = None)
            .evalMap(event => dequeued.update(toStringUft8(event.payload) :: _))
            .compile
            .drain
            .start

          message2 = "message2"
          _ <- queueClient.enqueue(queue, headerGen.generateOne, toByteVector(message2))
          _ <- dequeued.waitUntil(_.toSet == Set(message2))
        yield ()
    }

  test("allow marking and retrieving a processed event"):
    withRedisClient.asQueueClient().use { client =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      val clientId = RedisClientGenerators.clientIdGen.generateOne
      val messageId = RedisClientGenerators.messageIdGen.generateOne
      for
        _ <- client.findLastProcessed(clientId, queue).map(v => assert(v.isEmpty))

        _ <- client.markProcessed(clientId, queue, messageId)

        _ <- client
          .findLastProcessed(clientId, queue)
          .map(v => assert(v contains messageId))
      yield ()
    }

  private def toByteVector(v: String): ByteVector =
    ByteVector.encodeUtf8(v).fold(throw _, identity)

  private lazy val toStringUft8: ByteVector => String =
    _.decodeUtf8.fold(throw _, identity)

  private def enqueue(
      client: RedisClient,
      queueName: QueueName,
      payload: ByteVector
  ): IO[MessageId] =
    val message = Stream.emit[IO, XAddMessage[String, ByteVector]](
      XAddMessage(
        queueName.name,
        Map(
          MessageBodyKeys.payload -> payload,
          MessageBodyKeys.contentType -> toByteVector("illegal")
        )
      )
    )
    makeStreamingConnection(client)
      .flatMap(_.append(message))
      .map(id => MessageId(id.value))
      .compile
      .toList
      .map(_.head)

  private def makeStreamingConnection(
      client: RedisClient
  ): Stream[IO, Streaming[[A] =>> Stream[IO, A], String, ByteVector]] =
    RedisStream
      .mkStreamingConnection[IO, String, ByteVector](client, StringBytesCodec.instance)
