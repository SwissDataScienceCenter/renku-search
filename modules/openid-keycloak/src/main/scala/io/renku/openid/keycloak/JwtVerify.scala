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

import cats.Applicative
import cats.effect.*

import io.renku.search.jwt.RenkuToken
import org.http4s.client.Client

trait JwtVerify[F[_]]:
  def verify(token: String): F[Either[JwtError, RenkuToken]]

object JwtVerify:
  def apply[F[_]: Async](client: Client[F], config: JwtVerifyConfig): F[JwtVerify[F]] =
    val clock = Clock[F]
    DefaultJwtVerify[F](client, clock, config)

  def fixed[F[_]: Applicative](result: JwtError | RenkuToken): JwtVerify[F] =
    new JwtVerify[F] {
      def verify(token: String): F[Either[JwtError, RenkuToken]] =
        Applicative[F].pure(result match
          case a: JwtError   => Left(a)
          case b: RenkuToken => Right(b)
        )
    }
