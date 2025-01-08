package io.renku.search.api.routes

import cats.effect.*
import cats.syntax.all.*

import io.renku.search.common.CurrentVersion
import org.http4s.HttpRoutes
import sttp.tapir.*

final class VersionRoute[F[_]: Async](pathPrefix: List[String])
    extends RoutesDefinition[F]:
  private val baseEndpoint = endpoint.in(pathPrefix).tag("Information")

  val versionEndpoint =
    baseEndpoint.get
      .in("version")
      .out(borerJsonBody[CurrentVersion])
      .description("Returns version information")

  val versionRoute = RoutesDefinition
    .interpreter[F]
    .toRoutes(
      versionEndpoint.serverLogicSuccess[F](_ => CurrentVersion.get.pure[F])
    )

  override val docEndpoints: List[AnyEndpoint] =
    List(versionEndpoint)

  override val routes: HttpRoutes[F] =
    versionRoute
