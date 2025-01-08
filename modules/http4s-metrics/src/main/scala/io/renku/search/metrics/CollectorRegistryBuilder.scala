package io.renku.search.metrics

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import io.prometheus.client.{Collector as JCollector, CollectorRegistry}
import org.http4s.metrics.prometheus.PrometheusExportService

final case class CollectorRegistryBuilder[F[_]: Sync](
    collectors: Set[Collector],
    standardJVMMetrics: Boolean
):

  def withJVMMetrics: CollectorRegistryBuilder[F] =
    copy(standardJVMMetrics = true)

  def add(c: Collector): CollectorRegistryBuilder[F] =
    copy(collectors = collectors + c)

  def addAll(c: Iterable[Collector]): CollectorRegistryBuilder[F] =
    copy(collectors = collectors ++ c)

  def makeRegistry: Resource[F, CollectorRegistry] =
    val registry = new CollectorRegistry()
    (registerJVM(registry) >> registerCollectors(registry))
      .as(registry)

  private def registerCollectors(registry: CollectorRegistry): Resource[F, Unit] =
    collectors.toList.map(registerCollector(registry)).sequence.void

  private def registerCollector(registry: CollectorRegistry)(collector: Collector) =
    val F = Sync[F]
    val acq = F.blocking(collector.asJCollector.register[JCollector](registry))
    Resource.make(acq)(c => F.blocking(registry.unregister(c)))

  private def registerJVM(registry: CollectorRegistry) =
    if standardJVMMetrics then PrometheusExportService.addDefaults(registry)
    else Resource.pure[F, Unit](())

object CollectorRegistryBuilder:
  def apply[F[_]: Sync]: CollectorRegistryBuilder[F] =
    new CollectorRegistryBuilder[F](Set.empty, false)
