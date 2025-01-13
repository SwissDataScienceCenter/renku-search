package io.renku.search.api.auth

import cats.Monad
import cats.data.EitherT
import cats.syntax.all.*

import io.renku.openid.keycloak.JwtVerify
import io.renku.search.api.data.*
import scribe.Scribe

trait Authenticate[F[_]]:
  def apply(token: AuthToken): F[Either[String, AuthContext]]

object Authenticate:

  def apply[F[_]: Monad](verify: JwtVerify[F], logger: Scribe[F]): Authenticate[F] =
    new Authenticate[F] {
      def apply(token: AuthToken): F[Either[String, AuthContext]] =
        token match
          case AuthToken.None => Right(AuthContext.anonymous).pure[F]
          case AuthToken.AnonymousId(id) =>
            Right(AuthContext.anonymousId(id.value)).pure[F]
          case AuthToken.JwtToken(token) =>
            EitherT(verify.verify(token).map(ClaimToContext.from)).leftSemiflatMap { err =>
              logger.warn(err.sanitized, err.cause).as(err.sanitized)
            }.value
    }
