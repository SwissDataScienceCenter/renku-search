package io.renku.search.api.routes

import cats.effect.Async
import cats.syntax.all.*

import io.circe.syntax.given
import io.renku.search.BuildInfo
import org.http4s.HttpRoutes
import sttp.apispec.openapi.Server
import sttp.apispec.openapi.circe.given
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

final class OpenApiLegacyRoute[F[_]: Async](
    endpoints: List[AnyEndpoint]
) extends RoutesDefinition[F]:
  private val openAPIEndpoint =
    val docs = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoints,
        "Renku Search API",
        BuildInfo.gitDescribedVersion.getOrElse(BuildInfo.version)
      )
      .servers(
        List(
          Server(
            url = "/api/search",
            description = "Renku Search API".some
          )
        )
      )

    endpoint.get
      .in("search")
      .in("spec.json")
      .out(stringJsonBody)
      .description("OpenAPI docs")
      .serverLogic(_ => docs.asJson.spaces2.asRight.pure[F])

  override val docEndpoints = Nil

  override val routes: HttpRoutes[F] =
    RoutesDefinition
      .interpreter[F]
      .toRoutes(openAPIEndpoint)
