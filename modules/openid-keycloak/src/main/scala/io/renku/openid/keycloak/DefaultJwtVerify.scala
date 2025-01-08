package io.renku.openid.keycloak

import scala.concurrent.duration.*

import cats.Monad
import cats.data.EitherT
import cats.effect.*
import cats.syntax.all.*

import io.renku.openid.keycloak.DefaultJwtVerify.State
import io.renku.search.http.borer.BorerEntityJsonCodec
import io.renku.search.jwt.{JwtBorer, RenkuToken}
import org.http4s.Method.GET
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

final class DefaultJwtVerify[F[_]: Async](
    client: Client[F],
    state: Ref[F, State],
    clock: Clock[F],
    config: JwtVerifyConfig
) extends JwtVerify[F]
    with Http4sClientDsl[F]
    with BorerEntityJsonCodec:

  private val logger = scribe.cats.effect[F]

  def tryDecode(issuer: Uri, token: String): EitherT[F, JwtError, RenkuToken] =
    EitherT(state.get.flatMap(_.validate(issuer, clock, token)))

  def tryDecodeOnly(token: String): F[Either[JwtError, RenkuToken]] =
    JwtBorer.create[F](using clock).map { jwtb =>
      jwtb
        .decodeNoSignatureCheck(token)
        .toEither
        .leftMap(ex => JwtError.JwtValidationError(token, None, None, ex))
    }

  def verify(token: String): F[Either[JwtError, RenkuToken]] =
    tryDecodeOnly(token).flatMap {
      case Left(err)                                     => Left(err).pure[F]
      case Right(c) if !config.enableSignatureValidation => Right(c).pure[F]
      case Right(c) =>
        (for
          issuer <- EitherT.fromEither(readIssuer(c))
          res <- EitherT(
            tryDecode(issuer, token).foldF(
              updateCache(issuer, token),
              _.asRight.pure[F]
            )
          )
        yield res).value
    }

  def updateCache(issuer: Uri, token: String)(
      jwtError: JwtError
  ): F[Either[JwtError, RenkuToken]] =
    jwtError match
      case JwtError.JwtValidationError(_, _, Some(claim), _) =>
        (for
          _ <- EitherT.right(
            logger.info(
              s"Token validation failed, fetch JWKS from keycloak and try again: ${jwtError.getMessage()}"
            )
          )
          jwks <- fetchJWKSGuarded(issuer, claim)
          result <- EitherT(jwks.validate(clock)(token))
        yield result).value
      case e => Left(e).pure[F]

  def readIssuer(claim: RenkuToken): Either[JwtError, Uri] =
    for
      issuerUri <- Uri
        .fromString(claim.issuer.getOrElse(""))
        .leftMap(ex => JwtError.InvalidIssuerUrl(claim.issuer.getOrElse(""), ex))
      _ <- config.checkIssuerUrl(issuerUri)
    yield issuerUri

  def fetchJWKSGuarded(issuer: Uri, claim: RenkuToken): EitherT[F, JwtError, Jwks] =
    for
      _ <- checkLastUpdateDelay(issuer, config.minRequestDelay)
      result <- fetchJWKS(issuer, claim)
    yield result

  def checkLastUpdateDelay(issuer: Uri, min: FiniteDuration): EitherT[F, JwtError, Unit] =
    EitherT(
      clock.monotonic.flatMap(ct => state.modify(_.setLastUpdateDelay(issuer, ct))).map {
        case delay if delay > min => Right(())
        case _                    => Left(JwtError.TooManyValidationRequests(min))
      }
    )

  def fetchJWKS(issuerUri: Uri, claim: RenkuToken): EitherT[F, JwtError, Jwks] =
    for
      _ <- EitherT.right(
        clock.monotonic.flatMap(t => state.update(_.setLastUpdate(issuerUri, t)))
      )
      configUri = issuerUri.addPath(config.openIdConfigPath)

      _ <- EitherT.right(logger.debug(s"Fetch openid config from $configUri"))
      openIdCfg <- EitherT(client.expect[OpenIdConfig](GET(configUri)).attempt)
        .leftMap(ex => JwtError.OpenIdConfigError(configUri, ex))
      _ <- EitherT.right(logger.trace(s"Got openid-config response: $openIdCfg"))

      _ <- EitherT.right(logger.debug(s"Fetch jwks config from ${openIdCfg.jwksUri}"))
      jwks <- EitherT(client.expect[Jwks](GET(openIdCfg.jwksUri)).attempt)
        .leftMap(ex => JwtError.JwksError(openIdCfg.jwksUri, ex))

      _ <- EitherT.right(state.update(_.setJwks(issuerUri, jwks)))
      _ <- EitherT.right(
        logger.debug(s"Updated JWKS with keys: ${jwks.keys.map(_.keyId)}")
      )
    yield jwks

object DefaultJwtVerify:
  final case class State(jwks: Map[String, JwksState] = Map.empty):
    def get(issuer: Uri): JwksState =
      jwks.getOrElse(issuer.renderString, JwksState())

    def modify(issuer: Uri, f: JwksState => JwksState): State =
      copy(jwks = jwks.updatedWith(issuer.renderString) {
        case Some(v) => Some(f(v))
        case None    => Some(f(JwksState()))
      })

    def validate[F[_]: Monad](
        issuer: Uri,
        clock: Clock[F],
        token: String
    ): F[Either[JwtError, RenkuToken]] =
      get(issuer).jwks.validate(clock)(token)

    def setLastUpdate(issuer: Uri, time: FiniteDuration): State =
      modify(issuer, _.copy(lastUpdate = time))

    def setJwks(issuer: Uri, data: Jwks): State =
      modify(issuer, _.copy(jwks = data))

    def setLastUpdateDelay(issuer: Uri, now: FiniteDuration): (State, FiniteDuration) =
      val issuerUri = issuer.renderString
      val (ns, time) = get(issuer).lastUpdateDelay(now)
      (copy(jwks = jwks.updated(issuerUri, ns)), time)

  object State:
    def of(
        issuer: Uri,
        jwks: Jwks = Jwks.empty,
        lastUpdate: FiniteDuration = Duration.Zero,
        lastAccess: FiniteDuration = Duration.Zero
    ): State =
      State(Map(issuer.renderString -> JwksState(jwks, lastUpdate, lastAccess)))

  final case class JwksState(
      jwks: Jwks = Jwks.empty,
      lastUpdate: FiniteDuration = Duration.Zero,
      lastAccess: FiniteDuration = Duration.Zero
  ):
    def lastUpdateDelay(now: FiniteDuration): (JwksState, FiniteDuration) =
      (copy(lastAccess = now), now - lastUpdate)

  def apply[F[_]: Async](
      client: Client[F],
      clock: Clock[F],
      config: JwtVerifyConfig
  ): F[JwtVerify[F]] =
    Ref[F].of(State()).map(state => new DefaultJwtVerify(client, state, clock, config))
