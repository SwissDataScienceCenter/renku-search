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

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream

import io.renku.queue.client.QueueClient
import io.renku.redis.client.QueueName
import io.renku.search.events.*
import io.renku.search.events.EventMessage
import scribe.Scribe

trait MessageReader[F[_]]:
  def readEvents[A](using EventMessageDecoder[A]): Stream[F, EventMessage[A]]
  def readSyncEvents: Stream[F, SyncEventMessage]
  def markProcessed(id: MessageId): F[Unit]
  def markProcessedError(err: Throwable, id: MessageId)(using Scribe[F]): F[Unit]

object MessageReader:
  /** MessageReader that dequeues messages attempt to decode it. If decoding fails, the
    * message is marked as processed and the next message is read.
    */
  def apply[F[_]: Async](
      queueClient: Stream[F, QueueClient[F]],
      queue: QueueName,
      chunkSize: Int
  ): MessageReader[F] =
    new MessageReader[F]:
      private val logger: Scribe[F] = scribe.cats.effect[F]
      private val qnames = NonEmptyList.of(queue)

      def readSyncEvents: Stream[F, SyncEventMessage] =
        for
          client <- queueClient
          last <- Stream.eval(client.findLastProcessed(qnames))
          msg <- client.acquireSyncEventStream(qnames, chunkSize, last)
          _ <- Stream.eval(logMessage(msg: EventMessage[?]))
        yield msg

      def readEvents[A](using EventMessageDecoder[A]): Stream[F, EventMessage[A]] =
        for {
          client <- queueClient
          last <- Stream.eval(client.findLastProcessed(qnames))
          msg <- client.acquireMessageStream(qnames, chunkSize, last)
          _ <- Stream.eval(logMessage(msg))
        } yield msg

      override def markProcessed(id: MessageId): F[Unit] =
        queueClient.evalMap(_.markProcessed(qnames, id)).take(1).compile.drain

      override def markProcessedError(err: Throwable, id: MessageId)(using
          logger: Scribe[F]
      ): F[Unit] =
        markProcessed(id) >>
          logger.error(s"Processing messageId: $id for '$queue' failed", err)

      private def logMessage[A](m: EventMessage[A]): F[Unit] =
        lazy val values = m.payload.mkString(", ")
        logger.info(
          s"""Received message queue: ${queue.name}, id: ${m.id}, header: ${m.header} for: $values"""
        )
