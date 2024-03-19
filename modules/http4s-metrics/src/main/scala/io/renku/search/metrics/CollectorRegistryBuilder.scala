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

package io.renku.search.metrics

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import io.prometheus.client.{CollectorRegistry, Collector as JCollector}
import org.http4s.metrics.prometheus.PrometheusExportService

final class CollectorRegistryBuilder[F[_]: Sync]:
  private[this] val collectors = collection.mutable.Set[Collector]()
  private[this] var standardJVMMetrics: Boolean = false

  def withStandardJVMMetrics: CollectorRegistryBuilder[F] =
    standardJVMMetrics = true
    this

  def add(c: Collector): CollectorRegistryBuilder[F] =
    collectors += c
    this

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
    new CollectorRegistryBuilder[F]