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
import io.renku.redis.client.QueueName

private class UnprocessedCountGauge extends QueueGauge:

  private val underlying =
    Gauge
      .build()
      .name("redis_stream_unprocessed")
      .help("Total number of items left for processing in a stream")
      .labelNames("queue_name")
      .create()

  override val asJCollector: Gauge = underlying

  override def set(q: QueueName, v: Double): Unit =
    underlying.labels(q.name).set(v)
