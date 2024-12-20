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
