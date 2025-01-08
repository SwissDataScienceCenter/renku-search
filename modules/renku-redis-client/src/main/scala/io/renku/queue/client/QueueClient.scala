package io.renku.queue.client

import cats.data.NonEmptyList
import cats.effect.{Async, Resource}
import fs2.Stream

import io.renku.avro.codec.AvroEncoder
import io.renku.redis.client.{MessageId as _, *}
import io.renku.search.events.*

trait QueueClient[F[_]]:

  def enqueue[P: AvroEncoder](
      queueName: QueueName,
      msg: EventMessage[P]
  ): F[MessageId]

  def acquireHeaderEventsStream(
      queueNames: NonEmptyList[QueueName],
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, QueueMessage]

  def acquireMessageStream[T](
      queueNames: NonEmptyList[QueueName],
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  )(using EventMessageDecoder[T]): Stream[F, EventMessage[T]]

  def acquireSyncEventStream(
      queueNames: NonEmptyList[QueueName],
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, SyncEventMessage]

  def markProcessed(
      queueNames: NonEmptyList[QueueName],
      messageId: MessageId
  ): F[Unit]

  def findLastProcessed(queueNames: NonEmptyList[QueueName]): F[Option[MessageId]]

  def removeLastProcessed(queueNames: NonEmptyList[QueueName]): F[Unit]

  def getSize(queueName: QueueName): F[Long]

  def getSize(queueName: QueueName, from: MessageId): F[Long]

object QueueClient:

  // Be aware that it was observed that the client can lose the connection to Redis.
  // Because of that consider using the QueueClient.stream
  // that auto-refreshes (recreates) the connection every connectionRefreshInterval.
  def make[F[_]: Async](
      redisConfig: RedisConfig,
      clientId: ClientId
  ): Resource[F, QueueClient[F]] =
    RedisQueueClient.make[F](redisConfig).map(new QueueClientImpl[F](_, clientId))

  def stream[F[_]: Async](
      redisConfig: RedisConfig,
      clientId: ClientId
  ): Stream[F, QueueClient[F]] =
    val s = Stream
      .resource[F, QueueClient[F]](make(redisConfig, clientId))
      .interruptAfter(redisConfig.connectionRefreshInterval)
    s ++ stream(redisConfig, clientId)
