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

import cats.syntax.all.*
import java.security.PublicKey
import java.security.spec.ECPoint
import java.security.spec.ECParameterSpec
import java.security.AlgorithmParameters
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPublicKeySpec
import java.security.KeyFactory
import cats.MonadThrow

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
