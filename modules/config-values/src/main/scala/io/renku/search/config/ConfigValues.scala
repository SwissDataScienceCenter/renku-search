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
import java.util.concurrent.atomic.AtomicReference

final class ConfigValues(prefix: String = "RS") extends ConfigDecoders:
  private val values = new AtomicReference[Map[String, Option[String]]](Map.empty)

  private def config(
      name: String,
      default: Option[String]
  ): ConfigValue[Effect, String] = {
    val fullName = s"${prefix}_${name.toUpperCase}"
    values.updateAndGet(m => m.updated(fullName, default))
    val propName = fullName.toLowerCase.replace('_', '.')
    val cv = prop(propName).or(env(fullName))
    default.map(cv.default(_)).getOrElse(cv)
  }
  private def config(name: String): ConfigValue[Effect, String] = config(name, None)
  private def config(name: String, defval: String): ConfigValue[Effect, String] =
    config(name, Some(defval))

  def getAll: Map[String, Option[String]] = values.get()

  lazy val logLevel: ConfigValue[Effect, Int] =
    config("LOG_LEVEL", "2").as[Int]

  lazy val redisConfig: ConfigValue[Effect, RedisConfig] = {
    val host = config("REDIS_HOST", "localhost").as[RedisHost]
    val port = config("REDIS_PORT", "6379").as[RedisPort]
    val sentinel = config("REDIS_SENTINEL").as[Boolean].default(false)
    val maybeDB = config("REDIS_DB").as[RedisDB].option
    val maybePass = config("REDIS_PASSWORD").as[RedisPassword].option
    val maybeMasterSet = config("REDIS_MASTER_SET").as[RedisMasterSet].option
    val connectionRefresh =
      config("REDIS_CONNECTION_REFRESH_INTERVAL", "30 minutes").as[FiniteDuration]

    (host, port, sentinel, maybeDB, maybePass, maybeMasterSet, connectionRefresh)
      .mapN(RedisConfig.apply)
  }

  def eventQueue(eventType: String): ConfigValue[Effect, QueueName] =
    config(s"REDIS_QUEUE_$eventType").as[QueueName]

  lazy val retryOnErrorDelay: ConfigValue[Effect, FiniteDuration] =
    config("RETRY_ON_ERROR_DELAY", "10 seconds").as[FiniteDuration]

  lazy val metricsUpdateInterval: ConfigValue[Effect, FiniteDuration] =
    config("METRICS_UPDATE_INTERVAL", "15 seconds").as[FiniteDuration]

  def clientId(default: ClientId): ConfigValue[Effect, ClientId] =
    config("CLIENT_ID", default.value).as[ClientId]

  lazy val solrConfig: ConfigValue[Effect, SolrConfig] = {
    val url = config("SOLR_URL", "http://localhost:8983").as[Uri]
    val core = config("SOLR_CORE", "search-core-test")
    val maybeUser =
      (config("SOLR_USER", "admin"), config("SOLR_PASS"))
        .mapN(SolrUser.apply)
        .option
    val logMessageBodies =
      config("SOLR_LOG_MESSAGE_BODIES", "false").as[Boolean]
    (url, core, maybeUser, logMessageBodies).mapN(SolrConfig.apply)
  }

  def httpServerConfig(
      serviceName: String,
      defaultPort: Port
  ): ConfigValue[Effect, HttpServerConfig] =
    val bindAddress =
      config(s"${serviceName.toUpperCase}_HTTP_SERVER_BIND_ADDRESS", "0.0.0.0").as[Ipv4Address]
    val port =
      config(s"${serviceName.toUpperCase}_HTTP_SERVER_PORT", defaultPort.value.toString).as[Port]
    val shutdownTimeout =
      config("HTTP_SHUTDOWN_TIMEOUT", "30s").as[Duration]
    (bindAddress, port, shutdownTimeout).mapN(HttpServerConfig.apply)

  lazy val jwtVerifyConfig: ConfigValue[Effect, JwtVerifyConfig] = {
    val defaults = JwtVerifyConfig.default
    val enableSigCheck =
      config("JWT_ENABLE_SIGNATURE_CHECK", defaults.enableSignatureValidation.toString)
        .as[Boolean]
    val requestDelay =
      config("JWT_KEYCLOAK_REQUEST_DELAY", defaults.minRequestDelay.toString)
        .as[FiniteDuration]
    val openIdConfigPath =
      config("JWT_OPENID_CONFIG_PATH", defaults.openIdConfigPath)
    val allowedIssuers =
      config("JWT_ALLOWED_ISSUER_URL_PATTERNS")
        .as[List[UrlPattern]]
    (requestDelay, enableSigCheck, openIdConfigPath, allowedIssuers).mapN(
      JwtVerifyConfig.apply
    )
  }
