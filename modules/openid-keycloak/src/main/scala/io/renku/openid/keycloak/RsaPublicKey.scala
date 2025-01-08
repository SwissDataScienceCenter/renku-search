package io.renku.openid.keycloak

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec

import cats.MonadThrow
import cats.syntax.all.*

private object RsaPublicKey:

  def create(key: JsonWebKey.Rsa): Either[JwtError, PublicKey] =
    for
      mod <- BigIntDecode.decode(key.modulus)
      exp <- BigIntDecode.decode(key.exponent)
      kf <- Either
        .catchNonFatal(KeyFactory.getInstance("RSA"))
        .left
        .map(JwtError.SecurityApiError.apply)

      key <- Either
        .catchNonFatal(
          kf.generatePublic(RSAPublicKeySpec(mod.underlying, exp.underlying))
        )
        .left
        .map(JwtError.SecurityApiError.apply)
    yield key

  def createF[F[_]: MonadThrow](key: JsonWebKey.Rsa): F[PublicKey] =
    MonadThrow[F].fromEither(create(key))
