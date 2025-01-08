package io.renku.search.provision.metrics

import cats.Monad
import cats.data.NonEmptyList
import cats.syntax.all.*

import io.renku.queue.client.QueueClient
import io.renku.redis.client.QueueName

private class UnprocessedCountGaugeUpdater[F[_]: Monad](
    rc: QueueClient[F],
    gauge: UnprocessedCountGauge
) extends CollectorUpdater[F]:

  override def update(queueName: QueueName): F[Unit] =
    rc
      .findLastProcessed(NonEmptyList.of(queueName))
      .flatMap {
        case None     => rc.getSize(queueName)
        case Some(lm) => rc.getSize(queueName, lm)
      }
      .map(s => gauge.set(queueName, s.toDouble))
