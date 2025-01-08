package io.renku.openid.keycloak

import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

import cats.MonadThrow
import cats.syntax.all.*

private object EcPublicKey:

  def create(key: JsonWebKey.Ec): Either[JwtError, PublicKey] =
    for
      xn <- BigIntDecode.decode(key.x)
      yn <- BigIntDecode.decode(key.y)
      point = ECPoint(xn.underlying, yn.underlying)
      params <- Either
        .catchNonFatal {
          val p = AlgorithmParameters.getInstance("EC")
          p.init(new ECGenParameterSpec(key.curve.name))
          p.getParameterSpec(classOf[ECParameterSpec])
        }
        .left
        .map(JwtError.SecurityApiError.apply)
      pubspec = ECPublicKeySpec(point, params)
      kf <- Either
        // might need bc to support this properly?
        .catchNonFatal(KeyFactory.getInstance("EC"))
        .left
        .map(JwtError.SecurityApiError.apply)
      key <- Either
        .catchNonFatal(kf.generatePublic(pubspec))
        .left
        .map(JwtError.SecurityApiError.apply)
    yield key

  def createF[F[_]: MonadThrow](key: JsonWebKey.Ec): F[PublicKey] =
    MonadThrow[F].fromEither(create(key))
