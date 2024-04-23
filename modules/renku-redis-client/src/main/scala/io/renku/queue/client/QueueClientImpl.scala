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
import io.renku.search.events.{
  EventMessage,
  MessageHeader => MHeader,
  RenkuEventPayload,
  SchemaVersion
}
import io.renku.queue.client.DataContentType.*
import io.renku.redis.client.*
import scribe.Scribe

private class QueueClientImpl[F[_]: Async](redisQueueClient: RedisQueueClient[F])
    extends QueueClient[F]:

  private given Scribe[F] = scribe.cats[F]

  override def enqueue[P: AvroEncoder](
      queueName: QueueName,
      header: MessageHeader,
      payload: P
  ): F[MessageId] =
    val schemaHeader = header.toSchemaHeader(payload)
    val encHeader = AvroWriter(Header.SCHEMA$).writeJson(Seq(schemaHeader))
    val encPayload = header.dataContentType match {
      case Binary => AvroWriter(header.payloadSchema).write(Seq(payload))
      case Json   => AvroWriter(header.payloadSchema).writeJson(Seq(payload))
    }
    redisQueueClient.enqueue(queueName, encHeader, encPayload)

  def enqueue[P <: RenkuEventPayload: AvroEncoder](
      queueName: QueueName,
      schemaVersion: SchemaVersion,
      msg: EventMessage[P]
  ): F[MessageId] =
    val data = msg.toAvro(schemaVersion)
    redisQueueClient.enqueue(queueName, data.header, data.payload)

  override def acquireEventsStream(
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, QueueMessage] =

    def decodeHeader(rm: RedisMessage): F[Option[Header]] =
      MonadThrow[F]
        .catchNonFatal(AvroReader(Header.SCHEMA$).readJson[Header](rm.header).toList)
        .flatMap {
          case h :: Nil => h.some.pure[F]
          case h =>
            Scribe[F]
              .error(s"${h.size} header(s) in Redis instead of one")
              .as(Option.empty[Header])
        }

    redisQueueClient
      .acquireEventsStream(queueName, chunkSize, maybeOffset)
      .evalMap(rm => decodeHeader(rm).map(_.map(QueueMessage(rm.id, _, rm.payload))))
      .collect { case Some(qm) => qm }

  override def acquireHeaderEventsStream(
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, QueueHeaderMessage] =
    def decodeHeader(rm: RedisMessage): F[Option[MHeader]] =
      MHeader.fromByteVector(rm.header) match
        case Right(h)  => Some(h).pure[F]
        case Left(err) => Scribe[F].error(err).as(Option.empty[MHeader])

    redisQueueClient
      .acquireEventsStream(queueName, chunkSize, maybeOffset)
      .evalMap(rm =>
        decodeHeader(rm).map(_.map(QueueHeaderMessage(rm.id, _, rm.payload)))
      )
      .collect { case Some(qm) => qm }

  def acquireMessageStream[T <: RenkuEventPayload](
      queueName: QueueName,
      chunkSize: Int,
      maybeOffset: Option[MessageId],
      schemaSelect: SchemaSelect
  )(using AvroDecoder[T]): Stream[F, EventMessage[T]] =
    acquireHeaderEventsStream(queueName, chunkSize, maybeOffset)
      .map { m =>
        val schema = schemaSelect.select(m.header)
        m.toMessage[T](schema)
      }
      .evalMap {
        //TODO mark as processed for error case
        case Right(m)  => Some(m).pure[F]
        case Left(err) => Scribe[F].warn(s"Error decoding redis message: $err").as(None)
      }
      .unNone

  override def markProcessed(
      clientId: ClientId,
      queueName: QueueName,
      messageId: MessageId
  ): F[Unit] =
    redisQueueClient.markProcessed(clientId, queueName, messageId)

  override def findLastProcessed(
      clientId: ClientId,
      queueName: QueueName
  ): F[Option[MessageId]] =
    redisQueueClient.findLastProcessed(clientId, queueName)

  override def getSize(queueName: QueueName): F[Long] =
    redisQueueClient.getSize(queueName)

  override def getSize(queueName: QueueName, from: MessageId): F[Long] =
    redisQueueClient.getSize(queueName, from)
