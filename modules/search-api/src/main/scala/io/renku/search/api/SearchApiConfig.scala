package io.renku.search.api

import cats.syntax.all.*

import ciris.{ConfigValue, Effect}
import com.comcast.ip4s.port
import io.renku.openid.keycloak.JwtVerifyConfig
import io.renku.search.config.ConfigValues
import io.renku.search.http.HttpServerConfig
import io.renku.search.sentry.SentryConfig
import io.renku.solr.client.SolrConfig

final case class SearchApiConfig(
    solrConfig: SolrConfig,
    httpServerConfig: HttpServerConfig,
    jwtVerifyConfig: JwtVerifyConfig,
    sentryConfig: SentryConfig,
    verbosity: Int
)

object SearchApiConfig:
  private val configKeys = {
    val cv = ConfigValues()
    cv -> (
      cv.solrConfig,
      cv.httpServerConfig("SEARCH", port"8080"),
      cv.jwtVerifyConfig,
      cv.sentryConfig,
      cv.logLevel
    )
  }

  val configValues = configKeys._1

  val config: ConfigValue[Effect, SearchApiConfig] =
    val (_, keys) = configKeys
    keys.mapN(SearchApiConfig.apply)
