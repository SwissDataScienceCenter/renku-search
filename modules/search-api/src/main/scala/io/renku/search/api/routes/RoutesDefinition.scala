package io.renku.search.api.routes

import cats.effect.*

import io.renku.search.api.tapir.ApiSchema
import io.renku.search.http.borer.TapirBorerJson
import org.http4s.HttpRoutes
import sttp.tapir.*
import sttp.tapir.AnyEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.cors.CORSInterceptor

trait RoutesDefinition[F[_]] extends ApiSchema with TapirBorerJson:
  def routes: HttpRoutes[F]
  def docEndpoints: List[AnyEndpoint]

  extension [A, B, C, D, E](self: Endpoint[A, B, C, D, E])
    def in(path: List[String]) = path match
      case Nil     => self
      case n :: nn => self.in(nn.foldLeft(n: EndpointInput[Unit])(_ / _))

object RoutesDefinition:
  def interpreter[F[_]: Async] = Http4sServerInterpreter[F](
    Http4sServerOptions.customiseInterceptors
      .corsInterceptor(CORSInterceptor.default)
      .options
  )
