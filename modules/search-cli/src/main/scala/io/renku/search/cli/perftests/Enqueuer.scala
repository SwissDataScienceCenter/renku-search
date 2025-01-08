package io.renku.search.cli.perftests

import cats.effect.{Async, Ref, Resource}
import cats.syntax.all.*
import fs2.Pipe

import io.renku.avro.codec.AvroEncoder
import io.renku.queue.client.QueueClient
import io.renku.redis.client.ClientId
import io.renku.redis.client.QueueName

private trait Enqueuer[F[_]]:
  def enqueue: Pipe[F, QueueDelivery, Unit]

private object Enqueuer:
  def make[F[_]: Async](dryRun: DryRun, clientId: ClientId): Resource[F, Enqueuer[F]] =
    dryRun match {
      case DryRun.Yes =>
        Resource.pure(new DryRunEnqueuer[F])
      case DryRun.No(redisConfig) =>
        QueueClient.make(redisConfig, clientId).map(new RedisEnqueuer(_))
    }

private class RedisEnqueuer[F[_]: Async](qc: QueueClient[F]) extends Enqueuer[F]:

  private val dryRun = DryRunEnqueuer[F]

  override def enqueue: Pipe[F, QueueDelivery, Unit] =
    _.evalTap { delivery =>
      given AvroEncoder[delivery.P] = delivery.encoder
      qc.enqueue(delivery.queue, delivery.message).void
    }.through(dryRun.enqueue)

private class DryRunEnqueuer[F[_]: Async] extends Enqueuer[F]:

  private val cnt: Ref[F, Map[QueueName, Long]] = Ref.unsafe(Map.empty)

  override def enqueue: Pipe[F, QueueDelivery, Unit] =
    _.evalTap { delivery =>
      cnt.update(m => m.updatedWith(delivery.queue)(_.fold(1L.some)(v => (v + 1L).some)))
    }.evalMap { delivery =>
      cnt.get.map { m =>
        val cnt = m.getOrElse(delivery.queue, 1L)
        println(show"Event $cnt for $delivery")
      }
    }
