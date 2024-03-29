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
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.util.RedisSpec
import io.renku.search.GeneratorSyntax.*
import munit.CatsEffectSuite
import org.scalacheck.Gen
import org.scalacheck.Gen.alphaChar
import org.scalacheck.cats.implicits.*
import scodec.bits.ByteVector

class RedisQueueClientSpec extends CatsEffectSuite with RedisSpec:

  test("can enqueue and dequeue events"):
    withRedisClient.asRedisQueueClient().use { client =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      for
        dequeued <- SignallingRef.of[IO, List[(String, String)]](Nil)

        message1 = "message1"
        message1Head = "header1"
        _ <- client.enqueue(queue, toByteVector(message1Head), toByteVector(message1))

        streamingProcFiber <- client
          .acquireEventsStream(queue, chunkSize = 1, maybeOffset = None)
          .evalMap(event =>
            dequeued.update(event.header.asString -> event.payload.asString :: _)
          )
          .compile
          .drain
          .start
        _ <- dequeued.waitUntil(_ == List(message1Head -> message1))

        message2 = "message2"
        message2Head = "header2"
        _ <- client.enqueue(queue, toByteVector(message2Head), toByteVector(message2))
        _ <- dequeued
          .waitUntil(_.toSet == Set(message1Head -> message1, message2Head -> message2))

        _ <- streamingProcFiber.cancel
      yield ()
    }

  test("can start enqueueing events from the given messageId excluding"):
    withRedisClient.asRedisQueueClient().use { client =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      for
        dequeued <- SignallingRef.of[IO, List[String]](Nil)

        message1 = "message1"
        message1Id <- client.enqueue(queue, toByteVector("head1"), toByteVector(message1))

        streamingProcFiber <- client
          .acquireEventsStream(queue, chunkSize = 1, maybeOffset = message1Id.some)
          .evalMap(event => dequeued.update(event.payload.asString :: _))
          .compile
          .drain
          .start

        message2 = "message2"
        _ <- client.enqueue(queue, toByteVector("head2"), toByteVector(message2))
        _ <- dequeued.waitUntil(_.toSet == Set(message2))

        message3 = "message3"
        _ <- client.enqueue(queue, toByteVector("head3"), toByteVector(message3))
        _ <- dequeued.waitUntil(_.toSet == Set(message2, message3))
        _ <- streamingProcFiber.cancel
      yield ()
    }

  test("can skip events that are wrongly defined"):
    withRedisClient()
      .flatMap(rc => withRedisClient.asRedisQueueClient().tupleLeft(rc))
      .use { case (redisClient, queueClient) =>
        val queue = RedisClientGenerators.queueNameGen.generateOne
        for
          dequeued <- SignallingRef.of[IO, List[String]](Nil)

          _ <- enqueueWithoutHeader(redisClient, queue, toByteVector("message1"))

          streamingProcFiber <- queueClient
            .acquireEventsStream(queue, chunkSize = 1, maybeOffset = None)
            .evalMap(event => dequeued.update(event.payload.asString :: _))
            .compile
            .drain
            .start

          message2 = "message2"
          _ <- queueClient.enqueue(queue, toByteVector("head2"), toByteVector(message2))
          _ <- dequeued.waitUntil(_.toSet == Set(message2))
        yield ()
      }

  test("allow marking and retrieving a processed event"):
    withRedisClient.asRedisQueueClient().use { client =>
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

  test("can find out the total size of the given stream"):
    withRedisClient.asRedisQueueClient().use { client =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      val messages = (stringGen, stringGen).mapN(_ -> _).generateList(1, 30)
      for
        _ <- messages.traverse_ { case (h, p) =>
          client.enqueue(queue, toByteVector(h), toByteVector(p))
        }
        _ <- client.getSize(queue).map(s => assert(s == messages.size))
      yield ()
    }

  test("can find out a size of the given stream from the given MessageId"):
    withRedisClient.asRedisQueueClient().use { client =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      val olderMessages = (stringGen, stringGen).mapN(_ -> _).generateList(1, 30)
      val (msgH, msgP) = (stringGen, stringGen).mapN(_ -> _).generateOne
      val newerMessages = (stringGen, stringGen).mapN(_ -> _).generateList(1, 30)
      for
        _ <- olderMessages.traverse_ { case (h, p) =>
          client.enqueue(queue, toByteVector(h), toByteVector(p))
        }
        messageId <- client.enqueue(queue, toByteVector(msgH), toByteVector(msgP))
        _ <- newerMessages.traverse_ { case (h, p) =>
          client.enqueue(queue, toByteVector(h), toByteVector(p))
        }
        _ <- client.getSize(queue, messageId).map(s => assert(s == newerMessages.size))
      yield ()
    }

  private def toByteVector(v: String): ByteVector =
    ByteVector.encodeUtf8(v).fold(throw _, identity)

  extension (bv: ByteVector) def asString: String = bv.decodeUtf8.fold(throw _, identity)

  private def enqueueWithoutHeader(
      client: RedisClient,
      queueName: QueueName,
      payload: ByteVector
  ): IO[MessageId] =
    val message = Stream.emit[IO, XAddMessage[String, ByteVector]](
      XAddMessage(
        queueName.name,
        Map(MessageBodyKeys.payload -> payload)
      )
    )
    makeStreamingConnection(client)
      .flatMap(_.append(message))
      .map(id => MessageId(id.value))
      .compile
      .toList
      .map(_.head)

  private lazy val stringGen: Gen[String] =
    Gen
      .chooseNum(3, 10)
      .flatMap(Gen.stringOfN(_, alphaChar))

  private def makeStreamingConnection(
      client: RedisClient
  ): Stream[IO, Streaming[[A] =>> Stream[IO, A], String, ByteVector]] =
    RedisStream
      .mkStreamingConnection[IO, String, ByteVector](client, StringBytesCodec.instance)
