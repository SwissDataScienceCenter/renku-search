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

import cats.MonadThrow
import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream
import io.renku.avro.codec.decoders.all.given
import io.renku.avro.codec.encoders.all.given
import io.renku.avro.codec.{AvroDecoder, AvroEncoder, AvroReader, AvroWriter}
import io.renku.events.v1.Header
import io.renku.queue.client.DataContentType.*
import io.renku.search.events.*
import io.renku.redis.client.{MessageId => _, *}
import io.renku.queue.client.{MessageHeader => OldHeader}

import scribe.Scribe

private class QueueClientImpl[F[_]: Async](
    redisQueueClient: RedisQueueClient[F],
    clientId: ClientId
) extends QueueClient[F]:

  private given Scribe[F] = scribe.cats[F]

  override def enqueue[P: AvroEncoder](
      queueName: QueueName,
      header: OldHeader,
      payload: P
  ): F[MessageId] =
    val schemaHeader = header.toSchemaHeader(payload)
    val encHeader = AvroWriter(Header.SCHEMA$).writeJson(Seq(schemaHeader))
    val encPayload = header.dataContentType match {
      case Binary => AvroWriter(header.payloadSchema).write(Seq(payload))
      case Json   => AvroWriter(header.payloadSchema).writeJson(Seq(payload))
    }
    redisQueueClient.enqueue(queueName, encHeader, encPayload).map(MessageId.apply)

  def enqueue[P: AvroEncoder](
      queueName: QueueName,
      schemaVersion: SchemaVersion,
      msg: EventMessage[P]
  ): F[MessageId] =
    val data = msg.toAvro(schemaVersion)
    redisQueueClient.enqueue(queueName, data.header, data.payload).map(MessageId.apply)

  // deprecated
  override def acquireEventsStream(
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, io.renku.queue.client.QueueMessage] =

    def decodeHeader(rm: RedisMessage): F[Option[Header]] =
      MonadThrow[F]
        .catchNonFatal(AvroReader(Header.SCHEMA$).readJson[Header](rm.header).toList)
        .flatMap {
          case h :: Nil => h.some.pure[F]
          case h =>
            Scribe[F]
              .error(s"${h.size} header(s) in Redis instead of one")
              .as(Option.empty[Header])
              .flatTap(_ => markProcessed(queueName, MessageId(rm.id)))
        }

    redisQueueClient
      .acquireEventsStream(queueName, chunkSize, maybeOffset.map(_.value))
      .evalMap(rm =>
        decodeHeader(rm)
          .map(
            _.map(h => io.renku.queue.client.QueueMessage(rm.id, h, rm.payload))
          )
      )
      .collect { case Some(qm) => qm }

  override def acquireHeaderEventsStream(
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, QueueMessage] =
    def decodeHeader(rm: RedisMessage): F[Option[MessageHeader]] =
      MessageHeader.fromByteVector(rm.header) match
        case Right(h) => Some(h).pure[F]
        case Left(err) =>
          Scribe[F]
            .error(s"Error decoding message header: $err")
            .as(Option.empty[MessageHeader])
            .flatTap(_ => markProcessed(queueName, MessageId(rm.id)))

    redisQueueClient
      .acquireEventsStream(queueName, chunkSize, maybeOffset.map(_.value))
      .evalMap(rm =>
        decodeHeader(rm).map(_.map(QueueMessage(MessageId(rm.id), _, rm.payload)))
      )
      .collect { case Some(qm) => qm }

  def acquireMessageStream[T](
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  )(using d: EventMessageDecoder[T]): Stream[F, EventMessage[T]] =
    acquireHeaderEventsStream(queueName, chunkSize, maybeOffset).evalMap { m =>
      d.decode(m) match
        case Right(m) => Some(m).pure[F]
        case Left(err) =>
          Scribe[F]
            .warn(s"Error decoding redis message: $err")
            .as(None)
            .flatTap(_ => markProcessed(queueName, MessageId(m.id.value)))

    }.unNone

  override def markProcessed(queueName: QueueName, messageId: MessageId): F[Unit] =
    redisQueueClient.markProcessed(clientId, queueName, messageId.value)

  override def findLastProcessed(queueName: QueueName): F[Option[MessageId]] =
    redisQueueClient.findLastProcessed(clientId, queueName).map(_.map(MessageId.apply))

  override def getSize(queueName: QueueName): F[Long] =
    redisQueueClient.getSize(queueName)

  override def getSize(queueName: QueueName, from: MessageId): F[Long] =
    redisQueueClient.getSize(queueName, from.value)
