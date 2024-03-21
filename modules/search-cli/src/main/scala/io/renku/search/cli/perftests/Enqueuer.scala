package io.renku.search.cli.perftests

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.Pipe
import io.renku.avro.codec.AvroEncoder
import io.renku.queue.client.QueueClient

private trait Enqueuer[F[_]]:
  def enqueue: Pipe[F, QueueDelivery, Unit]

private object Enqueuer:
  def make[F[_]: Async](dryRun: DryRun): Resource[F, Enqueuer[F]] =
    dryRun match {
      case DryRun.Yes =>
        Resource.pure(new DryRunEnqueuer[F])
      case DryRun.No(redisConfig) =>
        QueueClient.make(redisConfig).map(new RedisEnqueuer(_))
    }

private class RedisEnqueuer[F[_]: Async](qc: QueueClient[F]) extends Enqueuer[F]:

  private val dryRun = DryRunEnqueuer[F]

  override def enqueue: Pipe[F, QueueDelivery, Unit] =
    _.evalTap { delivery =>
      given AvroEncoder[delivery.P] = delivery.encoder
      qc.enqueue(delivery.queue, delivery.header, delivery.payload).void
    }.through(dryRun.enqueue)

private class DryRunEnqueuer[F[_]: Async] extends Enqueuer[F]:

  override def enqueue: Pipe[F, QueueDelivery, Unit] =
    _.evalMap { delivery =>
      println(delivery.show).pure[F]
    }
