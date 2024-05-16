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

import org.http4s.Uri
import io.renku.search.http.borer.Http4sJsonCodec.given
import io.bullet.borer.derivation.key
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.Encoder
import io.bullet.borer.Decoder

final case class OpenIdConfig(
  issuer: Uri,
  @key("authorization_endpoint")  authorizationEndpoint: Uri,
  @key("token_endpoint") tokenEndpoint: Uri,
  @key("userinfo_endpoint") userInfoEndpoint: Uri,
  @key("end_session_endpoint") endSessionEndpoint: Uri,
  @key("jwks_uri") jwksUri: Uri,
  @key("claims_parameter_supported") claimsParameterSupporte: Boolean,
  @key("claims_supported") claimsSupported: List[String] = Nil,
  @key("grant_types_supported") grantTypesSupported: List[String] = Nil,
  @key("response_types_supported") responseTypesSupported: List[String] = Nil,
  @key("id_token_signing_alg_values_supported") idTokenSigningAlgSupported: List[String] = Nil,
  @key("userinfo_signing_alg_values_supported") userInfoSigningAlgSupported: List[String] = Nil,
  @key("authorization_signing_alg_values_supported") authorizationSigningAlgSupported: List[String] = Nil
)

object OpenIdConfig:

  given Encoder[OpenIdConfig] = MapBasedCodecs.deriveEncoder
  given Decoder[OpenIdConfig] = MapBasedCodecs.deriveDecoder
