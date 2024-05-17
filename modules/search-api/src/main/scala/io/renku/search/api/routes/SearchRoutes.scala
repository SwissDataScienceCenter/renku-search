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

package io.renku.search.api.routes

import cats.effect.Async
import cats.syntax.all.*

import io.renku.openid.keycloak.JwtError
import io.renku.openid.keycloak.JwtVerify
import io.renku.search.api.SearchApi
import io.renku.search.api.data.*
import io.renku.search.api.tapir.*
import io.renku.search.http.borer.TapirBorerJson
import io.renku.search.model.Id
import io.renku.search.query.docs.SearchQueryManual
import org.http4s.HttpRoutes
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.cors.CORSInterceptor

final class SearchRoutes[F[_]: Async](api: SearchApi[F], jwtVerify: JwtVerify[F])
    extends TapirBorerJson
    with TapirCodecs {

  private val logger = scribe.cats.effect[F]

  private val searchEndpointGet
      : Endpoint[AuthToken, QueryInput, String, SearchResult, Any] =
    endpoint.get
      .in("")
      .in(Params.queryInput)
      .securityIn(Params.renkuAuth)
      .errorOut(borerJsonBody[String])
      .out(Params.searchResult)
      .description(SearchQueryManual.markdown)

  def authenticate(token: AuthToken): F[Either[String, AuthContext]] = token match
    case AuthToken.None            => Right(AuthContext.anonymous).pure[F]
    case AuthToken.AnonymousId(id) => Right(AuthContext.anonymousId(id.value)).pure[F]
    case AuthToken.JwtToken(token) =>
      jwtVerify.verify(token).flatMap {
        case Right(claim) =>
          claim.subject
            .filter(_.nonEmpty)
            .map(userId => AuthContext.authenticated(Id(userId)))
            .toRight(s"Claim doesn't contain a subject: $claim")
            .pure[F]
        case Left(error: JwtError.TooManyValidationRequests) =>
          logger
            .warn("Token validation failed!", error)
            .as(
              Left(
                "Token validation failed due to too many validation requests, please try again later!"
              )
            )

        case Left(error) =>
          logger
            .warn("Token validation failed!", error)
            .as(
              Left("Token validation failed.")
            )
      }

  val endpoints: List[ServerEndpoint[Any, F]] =
    List(
      searchEndpointGet
        .serverSecurityLogic(authenticate)
        .serverLogic(api.query)
    )

  val routes: HttpRoutes[F] =
    Http4sServerInterpreter[F](
      Http4sServerOptions.customiseInterceptors
        .corsInterceptor(CORSInterceptor.default)
        .options
    )
      .toRoutes(endpoints)
}
