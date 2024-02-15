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

package io.renku.search.provision

import cats.syntax.all.*
import ciris.{ConfigValue, Effect}
import io.renku.queue.client.QueueName
import io.renku.redis.client.RedisConfig
import io.renku.search.config.ConfigValues
import io.renku.solr.client.SolrConfig

import scala.concurrent.duration.FiniteDuration

final case class SearchProvisionConfig(
    redisConfig: RedisConfig,
    queueName: QueueName,
    solrConfig: SolrConfig,
    retryOnErrorDelay: FiniteDuration
)

object SearchProvisionConfig:

  val config: ConfigValue[Effect, SearchProvisionConfig] =
    (
      ConfigValues.redisConfig,
      ConfigValues.eventsQueueName,
      ConfigValues.solrConfig,
      ConfigValues.retryOnErrorDelay
    ).mapN(SearchProvisionConfig.apply)
