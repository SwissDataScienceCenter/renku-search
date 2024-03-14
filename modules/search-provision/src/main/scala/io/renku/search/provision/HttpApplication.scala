package io.renku.search.provision

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.io.net.Network
import io.renku.search.http.metrics.MetricsRoutes
import io.renku.search.http.routes.OperationRoutes
import io.renku.search.metrics.CollectorRegistryBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.{HttpApp, HttpRoutes, Response}

object HttpApplication:

  def apply[F[_]: Async: Network]: Resource[F, HttpApp[F]] =
    MetricsRoutes[F](CollectorRegistryBuilder[F].withStandardJVMMetrics).makeRoutes
      .map(new HttpApplication[F](_).router)

final class HttpApplication[F[_]: Async](metricsRoutes: HttpRoutes[F])
    extends Http4sDsl[F]:

  private lazy val operationRoutes =
    Router[F](
      "/" -> OperationRoutes[F]
    )

  lazy val router: HttpApp[F] =
    (operationRoutes <+> metricsRoutes).orNotFound
