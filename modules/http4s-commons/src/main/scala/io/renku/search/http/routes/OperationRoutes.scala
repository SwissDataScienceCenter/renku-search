package io.renku.search.http.routes

import cats.effect.Async
import cats.syntax.all.*
import org.http4s.HttpRoutes
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import io.renku.search.common.CurrentVersion
import io.renku.search.http.borer.TapirBorerJson

object OperationRoutes extends TapirBorerJson {
  enum Paths {
    case Ping
    case Version

    lazy val name: String = productPrefix.toLowerCase()
  }

  def pingEndpoint[F[_]: Async] =
    endpoint.get
      .in(Paths.Ping.name)
      .out(stringBody)
      .description("Ping")
      .serverLogicSuccess[F](_ => "pong".pure[F])

  private given Schema[CurrentVersion] = Schema.derived

  def versionEndpoint[F[_]: Async] =
    endpoint.get
      .in(Paths.Version.name)
      .out(borerJsonBody[CurrentVersion])
      .description("Return version information")
      .serverLogicSuccess[F](_ => CurrentVersion.get.pure[F])

  def apply[F[_]: Async]: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(List(pingEndpoint, versionEndpoint))
}
