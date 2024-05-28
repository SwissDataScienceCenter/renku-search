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

package io.renku.search.jwt

import java.time.Instant
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.key
import RenkuToken.{Access, AccountRoles}
import io.bullet.borer.{Decoder, Encoder}
import io.bullet.borer.derivation.MapBasedCodecs

final case class RenkuToken(
    @key("exp") expirationTime: Option[Instant] = None,
    @key("iat") issuedAt: Option[Instant] = None,
    @key("nbf") notBefore: Option[Instant] = None,
    @key("auth_time") authTime: Option[Instant] = None,
    @key("jti") jwtId: Option[String] = None,
    @key("iss") issuer: Option[String] = None,
    @key("sub") subject: Option[String] = None,
    @key("typ") tokenType: Option[String] = None,
    @key("realm_access") realmAccess: Option[Access] = None,
    @key("resource_access") resourceAccess: Option[AccountRoles] = None,
    @key("scope") scopeStr: Option[String] = None,
    name: Option[String] = None,
    email: Option[String] = None,
    @key("email_verified") emailVerified: Boolean = false,
    groups: Set[String] = Set.empty,
    @key("preferred_username") preferredUsername: Option[String] = None
):

  lazy val isAdmin =
    realmAccess.exists(_.roles.contains("renku-admin"))

object RenkuToken:
  final case class Access(roles: Set[String])
  final case class AccountRoles(account: Access)

  private given Decoder[Instant] = Decoder.forLong.map(Instant.ofEpochSecond(_))
  private given Encoder[Instant] = Encoder.forLong.contramap(_.getEpochSecond())

  private given Decoder[Access] = MapBasedCodecs.deriveDecoder
  private given Encoder[Access] = MapBasedCodecs.deriveEncoder
  private given Decoder[AccountRoles] = MapBasedCodecs.deriveDecoder
  private given Encoder[AccountRoles] = MapBasedCodecs.deriveEncoder

  given Decoder[RenkuToken] = MapBasedCodecs.deriveDecoder
  given Encoder[RenkuToken] = MapBasedCodecs.deriveEncoder
