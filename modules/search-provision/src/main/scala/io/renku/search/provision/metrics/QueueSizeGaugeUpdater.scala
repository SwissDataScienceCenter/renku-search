package io.renku.search.provision.metrics

import cats.Monad
import cats.syntax.all.*

import io.renku.queue.client.QueueClient
import io.renku.redis.client.QueueName

private class QueueSizeGaugeUpdater[F[_]: Monad](
    qc: QueueClient[F],
    gauge: QueueSizeGauge
) extends CollectorUpdater[F]:

  override def update(queueName: QueueName): F[Unit] =
    qc.getSize(queueName).map(s => gauge.set(queueName, s.toDouble))
