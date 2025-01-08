package io.renku.search.provision.metrics

import scala.concurrent.duration.FiniteDuration

import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream

import io.renku.search.provision.metrics.SolrMetrics.*
import io.renku.search.solr.client.SearchSolrClient

private object SolrMetricsCollectorsUpdater:

  def apply[F[_]: Async](
      updateInterval: FiniteDuration,
      sc: SearchSolrClient[F]
  ): SolrMetricsCollectorsUpdater[F] =
    new SolrMetricsCollectorsUpdater[F](
      updateInterval,
      allCollectors.map(DocumentKindGaugeUpdater(sc, _)).toList
    )

private class SolrMetricsCollectorsUpdater[F[_]: Async](
    updateInterval: FiniteDuration,
    updaters: List[DocumentKindGaugeUpdater[F]]
):

  def run(): F[Unit] =
    Stream
      .awakeEvery[F](updateInterval)
      .evalMap(_ => updateCollectors)
      .compile
      .drain

  private def updateCollectors = updaters.traverse_(_.update())
