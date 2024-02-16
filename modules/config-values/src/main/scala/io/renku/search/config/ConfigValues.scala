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
import io.renku.redis.client.*
import io.renku.solr.client.{SolrConfig, SolrUser}
import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

object ConfigValues extends ConfigDecoders:

  private val prefix = "RS"

  val redisConfig: ConfigValue[Effect, RedisConfig] = {
    val host = env(s"${prefix}_REDIS_HOST").default("localhost").as[RedisHost]
    val port = env(s"${prefix}_REDIS_PORT").default("6379").as[RedisPort]
    val sentinel = env(s"${prefix}_REDIS_SENTINEL").as[Boolean].default(false)
    val maybeDB = env(s"${prefix}_REDIS_DB").as[RedisDB].option
    val maybePass = env(s"${prefix}_REDIS_PASSWORD").as[RedisPassword].option
    val maybeMasterSet = env(s"${prefix}_REDIS_MASTER_SET").as[RedisMasterSet].option

    (host, port, sentinel, maybeDB, maybePass, maybeMasterSet).mapN(RedisConfig.apply)
  }

  val eventsQueueName: ConfigValue[Effect, QueueName] =
    env(s"${prefix}_REDIS_QUEUE_NAME").default("events").as[QueueName]

  val retryOnErrorDelay: ConfigValue[Effect, FiniteDuration] =
    env(s"${prefix}_RETRY_ON_ERROR_DELAY").default("2 seconds").as[FiniteDuration]

  val solrConfig: ConfigValue[Effect, SolrConfig] = {
    val url = env(s"${prefix}_SOLR_URL").default("http://localhost:8983/solr").as[Uri]
    val core = env(s"${prefix}_SOLR_CORE").default("search-core-test")
    val maybeUser =
      (env(s"${prefix}_SOLR_USER").option -> env(s"${prefix}_SOLR_PASS").option)
        .mapN { case (maybeUsername, maybePass) =>
          (maybeUsername, maybePass).mapN(SolrUser.apply)
        }
    val defaultCommit =
      env(s"${prefix}_SOLR_DEFAULT_COMMIT_WITHIN")
        .default("0 seconds")
        .as[FiniteDuration]
        .option
    val logMessageBodies =
      env(s"${prefix}_SOLR_LOG_MESSAGE_BODIES").default("false").as[Boolean]
    (url, core, maybeUser, defaultCommit, logMessageBodies).mapN(SolrConfig.apply)
  }
