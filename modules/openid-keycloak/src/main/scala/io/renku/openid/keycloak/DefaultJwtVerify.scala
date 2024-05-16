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

import cats.effect.*
import pdi.jwt.JwtClaim
import org.http4s.client.Client
import DefaultJwtVerify.State

@annotation.nowarn
final class DefaultJwtVerify[F[_]](client: Client[F], state: Ref[F, State])
    extends JwtVerify[F]:

  // try to decode with jwks in cache
  // if fails:
  // - decode without signature check
  // - get the issuer url from claim
  // - fetch the jwks from issuer (=keycloak)
  //   - url is <issuer-url>/.well-known/openid-configuration
  //   - get the jwks_url from this response
  // - update cache, try again with new jwks (potentially fail permanently)

  def verify(token: String): F[Either[JwtError, JwtClaim]] =
    ???

object DefaultJwtVerify:
  final case class State(jwks: Jwks = Jwks.empty)
