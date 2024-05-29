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
