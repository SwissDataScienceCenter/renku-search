package io.renku.openid.keycloak

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.renku.search.http.borer.Http4sJsonCodec.given
import org.http4s.Uri

final case class OpenIdConfig(
    issuer: Uri,
    @key("authorization_endpoint") authorizationEndpoint: Uri,
    @key("token_endpoint") tokenEndpoint: Uri,
    @key("userinfo_endpoint") userInfoEndpoint: Uri,
    @key("end_session_endpoint") endSessionEndpoint: Uri,
    @key("jwks_uri") jwksUri: Uri,
    @key("claims_parameter_supported") claimsParameterSupporte: Boolean,
    @key("claims_supported") claimsSupported: List[String] = Nil,
    @key("grant_types_supported") grantTypesSupported: List[String] = Nil,
    @key("response_types_supported") responseTypesSupported: List[String] = Nil,
    @key("id_token_signing_alg_values_supported") idTokenSigningAlgSupported: List[
      String
    ] = Nil,
    @key("userinfo_signing_alg_values_supported") userInfoSigningAlgSupported: List[
      String
    ] = Nil,
    @key(
      "authorization_signing_alg_values_supported"
    ) authorizationSigningAlgSupported: List[String] = Nil
)

object OpenIdConfig:

  given Encoder[OpenIdConfig] = MapBasedCodecs.deriveEncoder
  given Decoder[OpenIdConfig] = MapBasedCodecs.deriveDecoder
