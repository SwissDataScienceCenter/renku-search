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
import com.comcast.ip4s.{Ipv4Address, Port}
import io.renku.search.common.UrlPattern
import io.renku.openid.keycloak.JwtVerifyConfig
import io.renku.redis.client.*
import io.renku.search.http.HttpServerConfig
import io.renku.solr.client.{SolrConfig, SolrUser}
import org.http4s.Uri

import scala.concurrent.duration.*

object ConfigValues extends ConfigDecoders:

  private val prefix = "RS"

  private def renv(name: String) =
    env(s"${prefix}_$name")

  val logLevel: ConfigValue[Effect, Int] =
    renv("LOG_LEVEL").default("2").as[Int]

  val redisConfig: ConfigValue[Effect, RedisConfig] = {
    val host = renv("REDIS_HOST").default("localhost").as[RedisHost]
    val port = renv("REDIS_PORT").default("6379").as[RedisPort]
    val sentinel = renv("REDIS_SENTINEL").as[Boolean].default(false)
    val maybeDB = renv("REDIS_DB").as[RedisDB].option
    val maybePass = renv("REDIS_PASSWORD").as[RedisPassword].option
    val maybeMasterSet = renv("REDIS_MASTER_SET").as[RedisMasterSet].option
    val connectionRefresh =
      renv("REDIS_CONNECTION_REFRESH_INTERVAL").as[FiniteDuration].default(30 minutes)

    (host, port, sentinel, maybeDB, maybePass, maybeMasterSet, connectionRefresh)
      .mapN(RedisConfig.apply)
  }

  def eventQueue(eventType: String): ConfigValue[Effect, QueueName] =
    renv(s"REDIS_QUEUE_$eventType").as[QueueName]

  val retryOnErrorDelay: ConfigValue[Effect, FiniteDuration] =
    renv("RETRY_ON_ERROR_DELAY").default("10 seconds").as[FiniteDuration]

  val metricsUpdateInterval: ConfigValue[Effect, FiniteDuration] =
    renv("METRICS_UPDATE_INTERVAL").default("15 seconds").as[FiniteDuration]

  def clientId(default: ClientId): ConfigValue[Effect, ClientId] =
    renv("CLIENT_ID").default(default.value).as[ClientId]

  val solrConfig: ConfigValue[Effect, SolrConfig] = {
    val url = renv("SOLR_URL").default("http://localhost:8983").as[Uri]
    val core = renv("SOLR_CORE").default("search-core-test")
    val maybeUser =
      (renv("SOLR_USER").default("admin"), renv("SOLR_PASS"))
        .mapN(SolrUser.apply)
        .option
    val logMessageBodies =
      renv("SOLR_LOG_MESSAGE_BODIES").default("false").as[Boolean]
    (url, core, maybeUser, logMessageBodies).mapN(SolrConfig.apply)
  }

  def httpServerConfig(
      prefix: String,
      defaultPort: Port
  ): ConfigValue[Effect, HttpServerConfig] =
    val bindAddress =
      renv(s"${prefix}_HTTP_SERVER_BIND_ADDRESS").default("0.0.0.0").as[Ipv4Address]
    val port =
      renv(s"${prefix}_HTTP_SERVER_PORT").default(defaultPort.value.toString).as[Port]
    val shutdownTimeout = renv(s"${prefix}_HTTP_SHUTDOWN_TIMEOUT").default("30s").as[Duration]
    (bindAddress, port, shutdownTimeout).mapN(HttpServerConfig.apply)

  val jwtVerifyConfig: ConfigValue[Effect, JwtVerifyConfig] = {
    val defaults = JwtVerifyConfig.default
    val enableSigCheck = renv("JWT_ENABLE_SIGNATURE_CHECK")
      .as[Boolean]
      .default(defaults.enableSignatureValidation)
    val requestDelay = renv("JWT_KEYCLOAK_REQUEST_DELAY")
      .as[FiniteDuration]
      .default(defaults.minRequestDelay)
    val openIdConfigPath =
      renv("JWT_OPENID_CONFIG_PATH").default(defaults.openIdConfigPath)
    val allowedIssuers =
      renv("JWT_ALLOWED_ISSUER_URL_PATTERNS")
        .as[List[UrlPattern]]
        .default(defaults.allowedIssuerUrls)
    (requestDelay, enableSigCheck, openIdConfigPath, allowedIssuers).mapN(
      JwtVerifyConfig.apply
    )
  }
