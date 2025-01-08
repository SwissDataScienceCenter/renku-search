package io.renku.search.api

import cats.effect.*
import cats.syntax.all.*
import fs2.io.net.Network

import io.renku.search.api.routes.OpenApiLegacyRoute
import io.renku.search.http.HttpServer
import io.renku.search.http.metrics.MetricsRoutes
import io.renku.search.http.routes.OperationRoutes
import io.renku.search.metrics.CollectorRegistryBuilder
import io.renku.search.sentry.Sentry
import org.http4s.HttpRoutes
import org.http4s.server.middleware.ResponseLogger
import org.http4s.server.middleware.{RequestId, RequestLogger}
import scribe.Scribe

object SearchServer:
  def create[F[_]: Async: Network](
      config: SearchApiConfig,
      app: ServiceRoutes[F],
      sentry: Sentry[F]
  ) =
    for
      routes <- makeHttpRoutes(app)
      logger = scribe.cats.effect[F]
      server <- HttpServer[F](config.httpServerConfig)
        .withDefaultErrorHandler(logger)
        .withHttpApp(routes.orNotFound)
        .build
    yield server

  def makeHttpRoutes[F[_]: Async](
      app: ServiceRoutes[F]
  ): Resource[F, HttpRoutes[F]] =
    val openApiLegacy = OpenApiLegacyRoute(app.docEndpoints).routes
    val opRoutes = OperationRoutes[F]
    val metrics = MetricsRoutes[F](CollectorRegistryBuilder[F].withJVMMetrics)
    for
      logger <- Resource.pure(scribe.cats.effect[F])
      businessRoutes <- metrics.makeRoutes(app.routes)
      routes = List(
        app.openapiDocRoutes,
        openApiLegacy,
        withMiddleware(logger, businessRoutes),
        opRoutes
      ).reduce(_ <+> _)
    yield routes

  def withMiddleware[F[_]: Async](
      logger: Scribe[F],
      httpRoutes: HttpRoutes[F]
  ): HttpRoutes[F] =
    middleWares[F](logger).foldLeft(httpRoutes)((r, mf) => mf(r))

  private def middleWares[F[_]: Async](
      logger: Scribe[F]
  ): List[HttpRoutes[F] => HttpRoutes[F]] =
    val log = (str: String) => logger.debug(str)
    List(
      RequestLogger
        .httpRoutes(
          logHeaders = true,
          logBody = false,
          logAction = log.some,
          redactHeadersWhen = HttpServer.sensitiveHeaders.contains
        ),
      RequestId.httpRoutes[F],
      ResponseLogger.httpRoutes(
        logHeaders = true,
        logBody = false,
        logAction = log.some,
        redactHeadersWhen = HttpServer.sensitiveHeaders.contains
      )
    )
