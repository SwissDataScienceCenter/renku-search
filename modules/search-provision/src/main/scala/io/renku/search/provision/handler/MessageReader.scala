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

package io.renku.search.provision.handler

import cats.Show
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.{Chunk, Stream}
import io.renku.queue.client.{QueueClient, QueueMessage, RequestId}
import io.renku.redis.client.{ClientId, MessageId, QueueName}
import io.renku.search.provision.QueueMessageDecoder
import scribe.Scribe

import scala.concurrent.duration.FiniteDuration

trait MessageReader[F[_]]:
  def read[A](using
      QueueMessageDecoder[F, A],
      Show[A]
  ): Stream[F, MessageReader.Message[A]]

  def readGrouped[A](chunkSize: Int, timeout: FiniteDuration)(using
      QueueMessageDecoder[F, A],
      Show[A],
      Async[F]
  ): Stream[F, Chunk[MessageReader.Message[A]]] =
    read[A].groupWithin(chunkSize, timeout)

  def markProcessed(id: MessageId): F[Unit]
  def markProcessedError(err: Throwable, id: MessageId)(using logger: Scribe[F]): F[Unit]

object MessageReader:
  final case class Message[A](raw: QueueMessage, decoded: Seq[A]):
    val id = raw.id
    val requestId: RequestId = RequestId(raw.header.requestId)
    def map[B](f: A => B): Message[B] = Message(raw, decoded.map(f))
    def stream[F[_]]: Stream[F, A] = Stream.emits(decoded).covary[F]

  /** MessageReader that dequeues messages attempt to decode it. If decoding fails, the
    * message is marked as processed and the next message is read.
    */
  def apply[F[_]: Async](
      queueClient: Resource[F, QueueClient[F]],
      queue: QueueName,
      clientId: ClientId,
      chunkSize: Int,
      connectionRefresh: FiniteDuration
  ): MessageReader[F] =
    new MessageReader[F]:
      val logger = scribe.cats.effect[F]

      def read[A](using QueueMessageDecoder[F, A], Show[A]): Stream[F, Message[A]] =
        val s = for {
          client <- Stream
            .resource[F, QueueClient[F]](queueClient)
            .interruptAfter(connectionRefresh)
          last <- Stream.eval(client.findLastProcessed(clientId, queue))
          qmsg <- client.acquireEventsStream(queue, chunkSize, last)
          dec <- Stream
            .eval(QueueMessageDecoder[F, A].decodeMessage(qmsg).attempt)
            .flatMap {
              case Right(dms) => Stream.emit(Message(qmsg, dms))
              case Left(err) =>
                for {
                  _ <- Stream.eval(
                    logger.error(
                      s"Decoding messageId: ${qmsg.id} for '${queue.name}' failed",
                      err
                    )
                  )
                  _ <- Stream.eval(client.markProcessed(clientId, queue, qmsg.id))
                } yield Message(qmsg, Seq.empty)
            }
          _ <- Stream.eval(logInfo(dec))
        } yield dec
        s ++ read

      def markProcessed(id: MessageId): F[Unit] =
        queueClient.use(_.markProcessed(clientId, queue, id))

      def markProcessedError(err: Throwable, id: MessageId)(using
          logger: Scribe[F]
      ): F[Unit] =
        markProcessed(id) >>
          logger.error(s"Processing messageId: ${id} for '${queue}' failed", err)

      private def logInfo[A: Show](m: Message[A]): F[Unit] =
        lazy val values = m.decoded.mkString_(", ")
        logger.info(
          s"""Received message queue: ${queue.name}, id: ${m.id}, source: ${m.raw.header.source}, type: ${m.raw.header.`type`} for: ${values}"""
        )
