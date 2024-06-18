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

import cats.effect.*
import cats.syntax.all.*
import io.renku.search.api.routes.OpenApiRoute
import io.renku.search.http.routes.OperationRoutes
import io.renku.search.http.metrics.MetricsRoutes
import io.renku.search.metrics.CollectorRegistryBuilder
import org.http4s.server.Router
import io.renku.search.http.HttpServer
import scribe.Scribe
import org.http4s.HttpApp
import org.http4s.server.middleware.{RequestId, RequestLogger}
import org.http4s.server.middleware.ResponseLogger
import fs2.io.net.Network

object SearchServer:

  def create[F[_]: Async: Network](config: SearchApiConfig, app: ServiceRoutes[F]) =
    for
      logger <- Resource.pure(scribe.cats.effect[F])
      openApiRoute = OpenApiRoute(app.docRoutes)
      opRoutes = OperationRoutes[F]
      metricRoutes = MetricsRoutes[F](CollectorRegistryBuilder[F].withJVMMetrics)
      businessRoutes <- metricRoutes.makeRoutes(app.routes)

      routes = Router[F](
        "/" -> (businessRoutes <+> opRoutes),
        "/api/search" -> openApiRoute.routes
      )

      server <- HttpServer[F](config.httpServerConfig)
        .withHttpApp(
          withMiddleware(logger, routes.orNotFound)
        )
        .build
    yield server

  def withMiddleware[F[_]: Async](logger: Scribe[F], httpApp: HttpApp[F]): HttpApp[F] =
    middleWares[F](logger).foldLeft(httpApp)((app, mf) => mf(app))

  private def middleWares[F[_]: Async](
      logger: Scribe[F]
  ): List[HttpApp[F] => HttpApp[F]] =
    val logAction = (str: String) => logger.info(str)
    List(
      RequestLogger
        .httpApp(logHeaders = true, logBody = false, logAction = logAction.some),
      RequestId.httpApp[F],
      ResponseLogger.httpApp(
        logHeaders = true,
        logBody = false,
        logAction = logAction.some
      )
    )
