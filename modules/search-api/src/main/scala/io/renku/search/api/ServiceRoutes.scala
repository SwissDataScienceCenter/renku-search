package io.renku.search.api

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.io.net.Network

import io.renku.openid.keycloak.JwtVerify
import io.renku.search.api.auth.Authenticate
import io.renku.search.api.routes.*
import io.renku.search.http.ClientBuilder
import io.renku.search.http.RetryConfig
import org.http4s.HttpRoutes
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Router

/** Defines the routes for the whole search service */
trait ServiceRoutes[F[_]] extends RoutesDefinition[F]:
  def config: SearchApiConfig
  def pathPrefix: List[String]
  def openapiDocRoutes: HttpRoutes[F]

object ServiceRoutes:
  def apply[F[_]: Async: Network](
      cfg: SearchApiConfig,
      prefix: List[String]
  ): Resource[F, ServiceRoutes[F]] =
    for
      logger <- Resource.pure(scribe.cats.effect[F])
      httpClient <- ClientBuilder(EmberClientBuilder.default[F])
        .withDefaultRetry(RetryConfig.default)
        .withLogging(logBody = false, logger)
        .build

      searchApi <- SearchApi[F](cfg.solrConfig)
      jwtVerify <- Resource.eval(JwtVerify(httpClient, cfg.jwtVerifyConfig))
      authenticate = Authenticate[F](jwtVerify, logger)

      routeDefs = List(
        SearchRoutes(searchApi, authenticate, Nil),
        VersionRoute[F](Nil)
      )
      legacyDefs = List(
        SearchLegacyRoutes(searchApi, authenticate, Nil)
      )
    yield new ServiceRoutes[F] {
      override val config = cfg
      override val pathPrefix = prefix
      private val pathPrefixStr = prefix.mkString("/", "/", "")

      override val docEndpoints =
        (routeDefs ++ legacyDefs).map(_.docEndpoints).reduce(_ ++ _)
      override val openapiDocRoutes: HttpRoutes[F] =
        OpenApiRoute(docEndpoints, prefix).routes
      override val routes = Router(
        pathPrefixStr -> routeDefs.map(_.routes).reduce(_ <+> _),
        "/search/query" -> legacyDefs.map(_.routes).reduce(_ <+> _),
        "/search" -> legacyDefs.map(_.routes).reduce(_ <+> _)
      )
    }
