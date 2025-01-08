package io.renku.search.api.routes

import cats.effect.Async

import io.renku.search.api.SearchApi
import io.renku.search.api.auth.Authenticate
import io.renku.search.api.data.*
import io.renku.search.api.tapir.*
import io.renku.search.query.docs.SearchQueryManual
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.*

final class SearchRoutes[F[_]: Async](
    api: SearchApi[F],
    authenticate: Authenticate[F],
    pathPrefix: List[String]
) extends RoutesDefinition[F] {

  private val logger = scribe.cats.effect[F]
  private val baseEndpoint = endpoint.in(pathPrefix).tag("Search")

  val queryEndpoint: Endpoint[AuthToken, QueryInput, String, SearchResult, Any] =
    baseEndpoint.get
      .in("query")
      .in(Params.queryInput)
      .securityIn(Params.renkuAuth)
      .errorOut(borerJsonBody[String])
      .errorOut(statusCode(StatusCode.UnprocessableEntity))
      .out(Params.searchResult)
      .description(SearchQueryManual.markdown)

  val queryRoute = RoutesDefinition
    .interpreter[F]
    .toRoutes(
      queryEndpoint
        .serverSecurityLogic(authenticate.apply)
        .serverLogic(api.query)
    )

  override val docEndpoints: List[AnyEndpoint] =
    List(queryEndpoint)

  override val routes: HttpRoutes[F] =
    queryRoute
}
