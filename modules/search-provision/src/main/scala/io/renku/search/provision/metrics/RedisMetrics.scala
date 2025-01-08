package io.renku.search.provision.metrics

import cats.Monad

import io.renku.queue.client.QueueClient

object RedisMetrics:

  val queueSizeGauge: QueueSizeGauge = QueueSizeGauge()
  val unprocessedGauge: UnprocessedCountGauge = UnprocessedCountGauge()

  def updaterFactories[F[_]: Monad]: List[QueueClient[F] => CollectorUpdater[F]] =
    List(
      new QueueSizeGaugeUpdater[F](_, queueSizeGauge),
      new UnprocessedCountGaugeUpdater[F](_, unprocessedGauge)
    )
