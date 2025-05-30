package io.renku.search.http

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.io.net.Network
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger as Http4sLogger
import org.http4s.server.Server
import org.http4s.HttpApp
import scribe.Scribe
import org.typelevel.ci.*

final class HttpServer[F[_]: Async](
    builder: EmberServerBuilder[F],
    middlewares: List[HttpApp[F] => HttpApp[F]]
):
  def build: Resource[F, Server] = builder.build

  def modify(f: EmberServerBuilder[F] => EmberServerBuilder[F]): HttpServer[F] =
    new HttpServer[F](f(builder), Nil)

  def withConfig(config: HttpServerConfig): HttpServer[F] =
    modify(
      _.withHost(config.bindAddress)
        .withPort(config.port)
        .withShutdownTimeout(config.shutdownTimeout)
    )

  def withDefaultErrorHandler(logger: Scribe[F]) =
    modify(_.withErrorHandler {
      case ex: DecodeFailure =>
        logger
          .warn("Error decoding message!", ex)
          .as(ex.toHttpResponse(HttpVersion.`HTTP/1.1`))
      case ex =>
        logger
          .error("Service raised an error!", ex)
          .as(Response(status = Status.InternalServerError))
    })

  def withMiddleware(m: HttpApp[F] => HttpApp[F]): HttpServer[F] =
    new HttpServer[F](builder, m :: middlewares)

  def withDefaultLogging(logger: Scribe[F]) =
    withMiddleware(
      Http4sLogger.httpApp(
        logHeaders = true,
        logBody = false,
        logAction = Some(msg => logger.debug(msg)),
        redactHeadersWhen = HttpServer.sensitiveHeaders.contains
      )
    )

  def withHttpApp(app: HttpApp[F]) =
    val applied = middlewares.foldLeft(app)((r, mf) => mf(r))
    modify(_.withHttpApp(applied))

  def withHttpRoutes(routes: HttpRoutes[F]) =
    withHttpApp(routes.orNotFound)

object HttpServer:
  val sensitiveHeaders: Set[CIString] =
    Headers.SensitiveHeaders + ci"Renku-Auth-Id-Token"

  def default[F[_]: Async: Network]: HttpServer[F] =
    new HttpServer[F](EmberServerBuilder.default[F], Nil)

  def apply[F[_]: Async: Network](config: HttpServerConfig): HttpServer[F] =
    default[F].withConfig(config)
