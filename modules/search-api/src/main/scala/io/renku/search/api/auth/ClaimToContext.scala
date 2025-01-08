package io.renku.search.api.auth

import scala.util.control.NoStackTrace

import io.renku.openid.keycloak.JwtError
import io.renku.search.api.data.AuthContext
import io.renku.search.jwt.RenkuToken
import io.renku.search.model.Id

private[auth] object ClaimToContext:
  final case class Failure(cause: Throwable, sanitized: String)
  final case class ClaimHasNoSubject(claim: RenkuToken)
      extends RuntimeException
      with NoStackTrace

  def apply(claim: RenkuToken): Either[Failure, AuthContext] =
    claim.subject
      .filter(_.nonEmpty)
      .map(userId =>
        if (claim.isAdmin) AuthContext.admin(Id(userId))
        else AuthContext.authenticated(Id(userId))
      )
      .toRight(Failure(ClaimHasNoSubject(claim), "Claim doesn't contain a subject"))

  def from(claim: Either[JwtError, RenkuToken]): Either[Failure, AuthContext] =
    claim.left
      .map {
        case error: JwtError.TooManyValidationRequests =>
          Failure(
            error,
            "Token validation failed due to too many validation requests, please try again later!"
          )
        case error =>
          Failure(error, "Token validation failed.")
      }
      .flatMap(apply)
