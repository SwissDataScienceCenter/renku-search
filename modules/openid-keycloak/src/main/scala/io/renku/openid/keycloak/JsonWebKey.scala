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

import io.bullet.borer.*
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import java.security.PublicKey

sealed trait JsonWebKey:
  def keyId: KeyId
  def keyType: KeyType
  def keyUse: KeyUse
  def algorithm: String
  def toPublicKey: Either[JwtError, PublicKey]

object JsonWebKey:

  @key("RSA")
  final case class Rsa(
      @key("kid") keyId: KeyId,
      @key("alg") algorithm: String,
      @key("use") keyUse: KeyUse,
      @key("n") modulus: String,
      @key("e") exponent: String,
      @key("x5c") certificateChain: List[String] = Nil,
      @key("x5t") certificateThumbPrint: Option[String] = None
  ) extends JsonWebKey:
    val keyType = KeyType.RSA
    lazy val toPublicKey: Either[JwtError, PublicKey] =
      RsaPublicKey.create(this)

  @key("EC")
  final case class Ec(
      @key("kid") keyId: KeyId,
      @key("alg") algorithm: String,
      @key("use") keyUse: KeyUse,
      @key("x") x: String,
      @key("y") y: String,
      @key("crv") curve: Curve
  ) extends JsonWebKey:
    val keyType = KeyType.EC
    lazy val toPublicKey: Either[JwtError, PublicKey] =
      EcPublicKey.create(this)

  @key("OKP")
  final case class Okp(
      @key("kid") keyId: KeyId,
      @key("alg") algorithm: String,
      @key("use") keyUse: KeyUse,
      @key("x") x: String,
      @key("crv") curve: Curve
  ) extends JsonWebKey:
    val keyType = KeyType.OKP
    lazy val toPublicKey: Either[JwtError, PublicKey] = Left(
      JwtError.UnsupportedPublicKey(keyType)
    )

  private given AdtEncodingStrategy =
    AdtEncodingStrategy.flat(typeMemberName = "kty")
  given Decoder[JsonWebKey] = {
    given Decoder[Ec] = MapBasedCodecs.deriveDecoder
    given Decoder[Rsa] = MapBasedCodecs.deriveDecoder
    given Decoder[Okp] = MapBasedCodecs.deriveDecoder

    MapBasedCodecs.deriveDecoder[JsonWebKey]
  }

  given Encoder[JsonWebKey] = {
    given Encoder[Ec] = MapBasedCodecs.deriveEncoder
    given Encoder[Rsa] = MapBasedCodecs.deriveEncoder
    given Encoder[Okp] = MapBasedCodecs.deriveEncoder

    MapBasedCodecs.deriveEncoder
  }
