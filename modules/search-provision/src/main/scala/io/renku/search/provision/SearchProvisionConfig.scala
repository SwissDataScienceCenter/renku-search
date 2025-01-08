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
