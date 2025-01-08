package io.renku.search.provision.metrics

import scala.concurrent.duration.FiniteDuration

import cats.NonEmptyParallel
import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream

import io.renku.queue.client.QueueClient
import io.renku.redis.client.ClientId
import io.renku.search.config.QueuesConfig
import io.renku.search.solr.client.SearchSolrClient

object MetricsCollectorsUpdater:
  def apply[F[_]: Async: NonEmptyParallel](
      clientId: ClientId,
      queuesConfig: QueuesConfig,
      updateInterval: FiniteDuration,
      queueClient: Stream[F, QueueClient[F]],
      solrClient: SearchSolrClient[F]
  ): MetricsCollectorsUpdater[F] =
    new MetricsCollectorsUpdater[F](
      RedisMetricsCollectorsUpdater[F](
        queuesConfig,
        updateInterval,
        queueClient
      ),
      SolrMetricsCollectorsUpdater[F](updateInterval, solrClient)
    )

class MetricsCollectorsUpdater[F[_]: Async: NonEmptyParallel](
    rcu: RedisMetricsCollectorsUpdater[F],
    scu: SolrMetricsCollectorsUpdater[F]
):
  def run(): F[Unit] =
    (rcu.run(), scu.run()).parTupled.void
