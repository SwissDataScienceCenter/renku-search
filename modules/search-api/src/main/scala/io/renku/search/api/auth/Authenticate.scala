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
            EitherT(verify.verify(token).map(ClaimToContext.from)).leftSemiflatMap {
              err => logger.warn(err.sanitized, err.cause).as(err.sanitized)
            }.value
    }
