package io.renku.openid.keycloak

import java.security.PublicKey

import cats.Monad
import cats.effect.Clock
import cats.syntax.all.*

import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.jwt.{JwtBorer, RenkuToken}
import pdi.jwt.JwtHeader

final case class Jwks(
    keys: List[JsonWebKey] = Nil
):
  def findById(id: KeyId): Option[JsonWebKey] =
    keys.find(_.keyId == id)

  def findFor(header: JwtHeader): Option[JsonWebKey] =
    header.keyId.map(KeyId(_)).flatMap(findById)

  def findPublicKey(header: JwtHeader): Either[JwtError, PublicKey] =
    for
      keyId <- header.keyId.map(KeyId(_)).toRight(JwtError.NoKeyId(header))
      wk <- findById(keyId).toRight(JwtError.KeyNotFound(keyId, keys))
      pk <- wk.toPublicKey
    yield pk

  def validate[F[_]: Monad](
      clock: Clock[F]
  )(jwt: String): F[Either[JwtError, RenkuToken]] =
    JwtBorer.create[F](using clock).map { jwtCheck =>
      for
        (header, claim, _) <- jwtCheck
          .decodeAllNoSignatureCheck(jwt)
          .toEither
          .leftMap(ex => JwtError.JwtValidationError(jwt, None, None, ex))

        pubKey <- findPublicKey(header)
          .leftMap(err =>
            JwtError.JwtValidationError(jwt, Some(header), Some(claim), err)
          )
        claim <- jwtCheck
          .decode(jwt, pubKey)
          .toEither
          .leftMap(ex => JwtError.JwtValidationError(jwt, Some(header), Some(claim), ex))
      yield claim
    }

object Jwks:
  val empty: Jwks = Jwks()

  given Decoder[Jwks] = MapBasedCodecs.deriveDecoder
  given Encoder[Jwks] = MapBasedCodecs.deriveEncoder
