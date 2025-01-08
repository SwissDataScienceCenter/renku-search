package io.renku.redis.client

import cats.Monoid
import cats.data.NonEmptyList
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.Stream

import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.FutureLift.*
import dev.profunktor.redis4cats.effect.{FutureLift, Log}
import dev.profunktor.redis4cats.effects.ScriptOutputType
import dev.profunktor.redis4cats.streams.data.{StreamingOffset, XAddMessage, XReadMessage}
import dev.profunktor.redis4cats.streams.{RedisStream, Streaming}
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import io.lettuce.core.api.StatefulRedisConnection
import scodec.bits.ByteVector
import scribe.Scribe

trait RedisQueueClient[F[_]] {

  def enqueue(
      queueName: QueueName,
      header: ByteVector,
      payload: ByteVector
  ): F[MessageId]

  def acquireEventsStream(
      queueNames: NonEmptyList[QueueName],
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, RedisMessage]

  def markProcessed(
      clientId: ClientId,
      queueNames: NonEmptyList[QueueName],
      messageId: MessageId
  ): F[Unit]

  def removeLastProcessed(
      clientId: ClientId,
      queueNames: NonEmptyList[QueueName]
  ): F[Unit]

  def findLastProcessed(
      clientId: ClientId,
      queueNames: NonEmptyList[QueueName]
  ): F[Option[MessageId]]

  def getSize(queueName: QueueName): F[Long]

  def getSize(queueName: QueueName, from: MessageId): F[Long]
}

object RedisQueueClient:

  def make[F[_]: Async](redisConfig: RedisConfig): Resource[F, RedisQueueClient[F]] =
    given Scribe[F] = scribe.cats[F]
    given Log[F] = RedisLogger[F]
    ClientCreator[F](redisConfig).makeClient.map(new RedisQueueClientImpl(_))

class RedisQueueClientImpl[F[_]: Async: Log](client: RedisClient)
    extends RedisQueueClient[F] {

  private val logger = scribe.cats.effect[F]
  given Monoid[ByteVector] = new Monoid[ByteVector] {
    def empty = ByteVector.empty
    def combine(x: ByteVector, y: ByteVector) = x ++ y
  }

  override def enqueue(
      queueName: QueueName,
      header: ByteVector,
      payload: ByteVector
  ): F[MessageId] =
    val messageBody = Map(
      MessageBodyKeys.Headers.name -> header,
      MessageBodyKeys.Payload.name -> payload
    )
    val message = Stream.emit[F, XAddMessage[String, ByteVector]](
      XAddMessage(queueName.name, messageBody)
    )
    makeStreamingConnection
      .flatMap(_.append(message))
      .map(id => id.value)
      .compile
      .toList
      .map(_.head)

  override def acquireEventsStream(
      queueNames: NonEmptyList[QueueName],
      chunkSize: Int,
      maybeOffset: Option[MessageId]
  ): Stream[F, RedisMessage] =
    val initialOffset: String => StreamingOffset[String] =
      maybeOffset
        .map(id => StreamingOffset.Custom[String](_, id))
        .getOrElse(StreamingOffset.All[String])

    def toMessage(rm: XReadMessage[String, ByteVector]): Option[RedisMessage] =
      (
        rm.body.get(MessageBodyKeys.Headers.name),
        rm.body.get(MessageBodyKeys.Payload.name)
      )
        .mapN(RedisMessage(rm.id.value, _, _))

    lazy val logInfo: ((XReadMessage[?, ?], Option[RedisMessage])) => F[Unit] = {
      case (m, None) =>
        logger.warn(
          s"Message '${m.id}' skipped as it has no '${MessageBodyKeys.Headers}' or '${MessageBodyKeys.Payload}'"
        )
      case _ => ().pure[F]
    }

    makeStreamingConnection >>= {
      _.read(queueNames.toList.toSet.map(_.name), chunkSize, initialOffset)
        .map(rm => rm -> toMessage(rm))
        .evalTap(logInfo)
        .collect { case (_, Some(m)) => m }
    }

  override def markProcessed(
      clientId: ClientId,
      queueNames: NonEmptyList[QueueName],
      messageId: MessageId
  ): F[Unit] =
    createStringCommands.use {
      _.set(formProcessedKey(clientId, queueNames), messageId).recoverWith { case ex =>
        logger.warn(
          s"Error setting last message-id '$messageId' for '${formProcessedKey(clientId, queueNames)}'",
          ex
        )
      }
    }

  override def removeLastProcessed(
      clientId: ClientId,
      queueNames: NonEmptyList[QueueName]
  ): F[Unit] =
    val key = formProcessedKey(clientId, queueNames)
    createStringCommands.use { cmd =>
      logger.debug(s"Delete last message-id for: $key") >>
        cmd.del(key).flatMap(n => logger.debug(s"Deleted $n")).recoverWith { case ex =>
          logger.warn(
            s"Error deleting last message-id '$key'",
            ex
          )
        }
    }

  override def findLastProcessed(
      clientId: ClientId,
      queueNames: NonEmptyList[QueueName]
  ): F[Option[MessageId]] =
    createStringCommands.use {
      _.get(formProcessedKey(clientId, queueNames))
    }

  private def formProcessedKey(clientId: ClientId, queueNames: NonEmptyList[QueueName]) =
    queueNames match
      case NonEmptyList(h, Nil) => s"$h.$clientId"
      case _ =>
        val names = queueNames.toList
          .map(n => ByteVector.view(n.name.getBytes()))
          .combineAll
          .md5
          .toHex
        s"$clientId.$names"

  override def getSize(queueName: QueueName): F[Long] =
    val xlen: StatefulRedisConnection[String, ByteVector] => F[Long] =
      _.async().xlen(queueName.name).futureLift.map(_.longValue())

    makeLettuceStreamingConnection(StringBytesCodec.instance)
      .use(xlen)

  override def getSize(queueName: QueueName, from: MessageId): F[Long] =
    val script =
      """local xrange = redis.call('XRANGE', KEYS[1], ARGV[1], ARGV[2])
        |return #xrange - 1""".stripMargin
    createStringCommands.use(
      _.eval(
        script,
        ScriptOutputType.Integer,
        List(queueName.name),
        List(from, "+")
      )
    )

  private def makeStreamingConnection
      : Stream[F, Streaming[[A] =>> Stream[F, A], String, ByteVector]] =
    RedisStream
      .mkStreamingConnection[F, String, ByteVector](client, StringBytesCodec.instance)

  private def makeLettuceStreamingConnection[K, V](
      codec: RedisCodec[K, V]
  ): Resource[F, StatefulRedisConnection[K, V]] = {
    val acquire =
      FutureLift[F].lift(
        client.underlying.connectAsync[K, V](codec.underlying, client.uri.underlying)
      )

    val release: StatefulRedisConnection[K, V] => F[Unit] = c =>
      FutureLift[F].lift(c.closeAsync()) *>
        Log[F].info(s"Releasing Streaming connection: ${client.uri.underlying}")

    Resource.make(acquire)(release)
  }

  private def createStringCommands: Resource[F, RedisCommands[F, String, String]] =
    Redis[F].fromClient(client, RedisCodec.Utf8)
}
