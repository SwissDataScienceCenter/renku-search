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

package io.renku.search.provision.metrics

import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream
import io.renku.search.provision.metrics.SolrMetrics.*
import io.renku.search.solr.client.SearchSolrClient

import scala.concurrent.duration.FiniteDuration

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
