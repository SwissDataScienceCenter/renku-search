package io.renku.search.http

import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, BasicCredentials, Request}

trait HttpClientDsl[F[_]] extends Http4sClientDsl[F] {

  implicit final class MoreRequestDsl(req: Request[F]) {
    def withBasicAuth(cred: Option[BasicCredentials]): Request[F] =
      cred.map(c => req.putHeaders(Authorization(c))).getOrElse(req)

    def withBearerToken(token: Option[String]): Request[F] =
      token match
        case Some(t) =>
          req.putHeaders(
            Authorization(org.http4s.Credentials.Token(AuthScheme.Bearer, t))
          )
        case None => req
  }
}
