/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
