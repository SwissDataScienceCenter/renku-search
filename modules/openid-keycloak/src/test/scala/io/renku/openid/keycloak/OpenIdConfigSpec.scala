package io.renku.openid.keycloak

import io.bullet.borer.Json
import munit.FunSuite
import org.http4s.implicits.*

class OpenIdConfigSpec extends FunSuite with JwtResources:

  test("parse json"):
    val decoded = Json.decode(configEndpointData.getBytes()).to[OpenIdConfig].value
    assertEquals(
      decoded.authorizationEndpoint,
      uri"https://ci-renku-3622.dev.renku.ch/auth/realms/Renku/protocol/openid-connect/auth"
    )
    assertEquals(
      decoded.issuer,
      uri"https://ci-renku-3622.dev.renku.ch/auth/realms/Renku"
    )
    assertEquals(
      decoded.jwksUri,
      uri"https://ci-renku-3622.dev.renku.ch/auth/realms/Renku/protocol/openid-connect/certs"
    )
    assert(decoded.authorizationSigningAlgSupported.contains("RS512"))
