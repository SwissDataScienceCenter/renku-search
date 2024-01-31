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

package io.renku.search.config

import cats.syntax.all.*
import ciris.*
import io.renku.queue.client.QueueName
import io.renku.redis.client.RedisUrl
import io.renku.solr.client.SolrConfig
import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

object ConfigValues extends ConfigDecoders:

  private val prefix = "RS"

  val redisUrl: ConfigValue[Effect, RedisUrl] =
    env(s"${prefix}_REDIS_URL").default("redis://localhost:6379").as[RedisUrl]

  val eventsQueueName: ConfigValue[Effect, QueueName] =
    env(s"${prefix}_REDIS_QUEUE_NAME").default("events").as[QueueName]

  val retryOnErrorDelay: ConfigValue[Effect, FiniteDuration] =
    env(s"${prefix}_RETRY_ON_ERROR_DELAY").default("2 seconds").as[FiniteDuration]

  val solrConfig: ConfigValue[Effect, SolrConfig] = {
    val url = env(s"${prefix}_SOLR_URL").default("http://localhost:8983/solr").as[Uri]
    val core = env(s"${prefix}_SOLR_CORE").default("search-core-test")
    val defaultCommit =
      env(s"${prefix}_SOLR_DEFAULT_COMMIT_WITHIN").default("0").as[FiniteDuration].option
    val logMessageBodies =
      env(s"${prefix}_SOLR_LOG_MESSAGE_BODIES").default("false").as[Boolean]
    (url, core, defaultCommit, logMessageBodies).mapN(SolrConfig.apply)
  }
