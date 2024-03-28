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

import io.prometheus.client.Gauge
import io.renku.search.metrics.Collector
import io.renku.search.model.EntityType
import io.renku.search.solr.documents.DocumentKind

private class DocumentKindGauge(val entityType: EntityType) extends Collector:

  private val underlying =
    Gauge
      .build()
      .name(s"solr_${entityType.name.toLowerCase}_by_kind")
      .help(s"Total number of '$entityType' entities by kind")
      .labelNames("kind")
      .create()

  override val asJCollector: Gauge = underlying

  def set(l: DocumentKind, v: Double): Unit =
    underlying.labels(l.name).set(v)
