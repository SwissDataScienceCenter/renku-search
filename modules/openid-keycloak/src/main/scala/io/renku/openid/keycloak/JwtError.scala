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

import pdi.jwt.JwtHeader

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
