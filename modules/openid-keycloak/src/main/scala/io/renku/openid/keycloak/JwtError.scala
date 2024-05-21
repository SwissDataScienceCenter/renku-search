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

import scala.concurrent.duration.FiniteDuration

import org.http4s.Uri
import pdi.jwt.JwtClaim
import pdi.jwt.JwtHeader
import io.renku.search.common.UrlPattern

sealed trait JwtError extends Throwable

object JwtError:

  final case class UnsupportedPublicKey(keyType: KeyType)
      extends RuntimeException(s"Unsupported key type for creating public key: $keyType")
      with JwtError:
    override def fillInStackTrace(): Throwable = this

  final case class NoKeyId(header: JwtHeader)
      extends RuntimeException(s"No key-id in jwt header: $header")
      with JwtError:
    override def fillInStackTrace(): Throwable = this

  final class BigIntDecodeError(val value: String, val message: String)
      extends RuntimeException(
        s"Error decoding base64 value '$value' to BigInt: $message"
      )
      with JwtError:
    override def fillInStackTrace(): Throwable = this

  final case class SecurityApiError(cause: Throwable)
      extends RuntimeException(cause)
      with JwtError

  final case class KeyNotFound(keyId: KeyId, keys: List[JsonWebKey])
      extends RuntimeException(s"Key $keyId not found in JWKS ${keys.map(_.keyId)}")
      with JwtError

  final case class JwtValidationError(
      jwt: String,
      header: Option[JwtHeader],
      claim: Option[JwtClaim],
      cause: Throwable
  ) extends RuntimeException(
        s"Error decoding token (header=$header, claimExists=${claim.isDefined}): ${cause.getMessage}",
        cause
      )
      with JwtError

  final case class InvalidIssuerUrl(url: String, cause: Throwable)
      extends RuntimeException(
        s"Invalid issuer uri '$url' in claim: ${cause.getMessage()}",
        cause
      )
      with JwtError

  final case class OpenIdConfigError(uri: Uri, cause: Throwable)
      extends RuntimeException(
        s"Error getting openid config from: ${uri.renderString}",
        cause
      )
      with JwtError

  final case class JwksError(uri: Uri, cause: Throwable)
      extends RuntimeException(
        s"Error getting jwks config from: ${uri.renderString}",
        cause
      )
      with JwtError

  final case class TooManyValidationRequests(minDelay: FiniteDuration)
      extends RuntimeException(s"Too many validation attempts within $minDelay")
      with JwtError

  final case class ForbiddenIssuer(uri: Uri, allowed: List[UrlPattern])
      extends RuntimeException(
        s"Issuer '${uri.renderString}' not configured in the allowed issuers (${allowed
            .map(_.render)})"
      )
      with JwtError
