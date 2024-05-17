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

package io.renku.openid.keycloak

import scala.concurrent.duration.*

import cats.data.EitherT
import cats.effect.*
import cats.syntax.all.*

import io.renku.openid.keycloak.DefaultJwtVerify.State
import io.renku.search.http.borer.BorerEntityJsonCodec
import io.renku.search.jwt.JwtBorer
import org.http4s.Method.GET
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import pdi.jwt.JwtClaim

final class DefaultJwtVerify[F[_]: Async](
    client: Client[F],
    state: Ref[F, State],
    clock: Clock[F],
    config: JwtVerifyConfig
) extends JwtVerify[F]
    with Http4sClientDsl[F]
    with BorerEntityJsonCodec:

  private val logger = scribe.cats.effect[F]

  def tryDecode(token: String) =
    EitherT(state.get.flatMap(_.jwks.validate(clock)(token)))

  def tryDecodeOnly(token: String): F[Either[JwtError, JwtClaim]] =
    JwtBorer.create[F](using clock).map { jwtb =>
      jwtb
        .decodeNoSignatureCheck(token)
        .toEither
        .leftMap(ex => JwtError.JwtValidationError(token, None, None, ex))
    }

  def verify(token: String): F[Either[JwtError, JwtClaim]] =
    if (!config.enableSignatureValidation) tryDecodeOnly(token)
    else tryDecode(token).foldF(updateCache(token), _.asRight.pure[F])

  def updateCache(token: String)(jwtError: JwtError): F[Either[JwtError, JwtClaim]] =
    jwtError match
      case JwtError.JwtValidationError(_, _, Some(claim), _) =>
        (for
          _ <- EitherT.right(
            logger.info(
              s"Token validation failed, fetch JWKS from keycloak and try again: ${jwtError.getMessage()}"
            )
          )
          jwks <- fetchJWKSGuarded(claim)
          result <- EitherT(jwks.validate(clock)(token))
        yield result).value
      case e => Left(e).pure[F]

  def fetchJWKSGuarded(claim: JwtClaim): EitherT[F, JwtError, Jwks] =
    for
      _ <- checkLastUpdateDelay(config.minRequestDelay)
      result <- fetchJWKS(claim)
    yield result

  def checkLastUpdateDelay(min: FiniteDuration): EitherT[F, JwtError, Unit] =
    EitherT(
      clock.monotonic.flatMap(ct => state.modify(_.lastUpdateDelay(ct))).map {
        case delay if delay > min => Right(())
        case _                    => Left(JwtError.TooManyValidationRequests(min))
      }
    )

  def fetchJWKS(claim: JwtClaim): EitherT[F, JwtError, Jwks] =
    for
      _ <- EitherT.right(
        clock.monotonic.flatMap(t => state.update(_.copy(lastUpdate = t)))
      )
      issuerUri <- EitherT.fromEither(
        Uri
          .fromString(claim.issuer.getOrElse(""))
          .leftMap(ex => JwtError.InvalidIssuerUrl(claim.issuer.getOrElse(""), ex))
      )
      configUri = issuerUri.addPath(config.openIdConfigPath)

      _ <- EitherT.right(logger.debug(s"Fetch openid config from $configUri"))
      openIdCfg <- EitherT(client.expect[OpenIdConfig](GET(configUri)).attempt)
        .leftMap(ex => JwtError.OpenIdConfigError(configUri, ex))
      _ <- EitherT.right(logger.trace(s"Got openid-config response: $openIdCfg"))

      _ <- EitherT.right(logger.debug(s"Fetch jwks config from ${openIdCfg.jwksUri}"))
      jwks <- EitherT(client.expect[Jwks](GET(openIdCfg.jwksUri)).attempt)
        .leftMap(ex => JwtError.JwksError(openIdCfg.jwksUri, ex))

      _ <- EitherT.right(state.update(_.copy(jwks = jwks)))
      _ <- EitherT.right(
        logger.debug(s"Updated JWKS with keys: ${jwks.keys.map(_.keyId)}")
      )
    yield jwks

object DefaultJwtVerify:
  final case class State(
      jwks: Jwks = Jwks.empty,
      lastUpdate: FiniteDuration = Duration.Zero,
      lastAccess: FiniteDuration = Duration.Zero
  ):
    def lastUpdateDelay(now: FiniteDuration): (State, FiniteDuration) =
      (copy(lastAccess = now), now - lastUpdate)

  def apply[F[_]: Async](
      client: Client[F],
      clock: Clock[F],
      config: JwtVerifyConfig
  ): F[JwtVerify[F]] =
    Ref[F].of(State()).map(state => new DefaultJwtVerify(client, state, clock, config))
