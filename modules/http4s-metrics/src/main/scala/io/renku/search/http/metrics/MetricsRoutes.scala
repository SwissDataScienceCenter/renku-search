package io.renku.search.http.metrics

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import io.renku.search.metrics.CollectorRegistryBuilder
import org.http4s.HttpRoutes
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import org.http4s.server.middleware.Metrics

final class MetricsRoutes[F[_]: Sync](registryBuilder: CollectorRegistryBuilder[F]):

  def makeRoutes(measuredRoutes: HttpRoutes[F]): Resource[F, HttpRoutes[F]] =
    for
      registry <- registryBuilder.makeRegistry
      metrics <- Prometheus.metricsOps[F](registry, prefix = "server")
    yield PrometheusExportService(registry).routes <+> Metrics[F](metrics)(measuredRoutes)

  def makeRoutes: Resource[F, HttpRoutes[F]] =
    registryBuilder.makeRegistry.map(PrometheusExportService.apply).map(_.routes)
