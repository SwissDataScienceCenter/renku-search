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

import scala.concurrent.duration.FiniteDuration

import cats.syntax.all.*

import ciris.{ConfigValue, Effect}
import com.comcast.ip4s.port
import io.renku.redis.client.{ClientId, RedisConfig}
import io.renku.search.config.{ConfigValues, QueuesConfig}
import io.renku.search.http.HttpServerConfig
import io.renku.search.sentry.SentryConfig
import io.renku.solr.client.SolrConfig

final case class SearchProvisionConfig(
    redisConfig: RedisConfig,
    solrConfig: SolrConfig,
    retryOnErrorDelay: FiniteDuration,
    metricsUpdateInterval: FiniteDuration,
    verbosity: Int,
    queuesConfig: QueuesConfig,
    httpServerConfig: HttpServerConfig,
    sentryConfig: SentryConfig,
    clientId: ClientId
)

object SearchProvisionConfig:

  private val configKeys = {
    val cv = ConfigValues()
    cv -> (
      cv.redisConfig,
      cv.solrConfig,
      cv.retryOnErrorDelay,
      cv.metricsUpdateInterval,
      cv.logLevel,
      QueuesConfig.config(cv),
      cv.httpServerConfig("PROVISION", defaultPort = port"8081"),
      cv.sentryConfig,
      cv.clientId(ClientId("search-provisioner"))
    )
  }

  val configValues = configKeys._1

  val config: ConfigValue[Effect, SearchProvisionConfig] =
    val (_, keys) = configKeys
    keys.mapN(SearchProvisionConfig.apply)
