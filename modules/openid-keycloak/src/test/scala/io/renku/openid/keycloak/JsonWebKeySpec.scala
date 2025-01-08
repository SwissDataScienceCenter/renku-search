package io.renku.openid.keycloak

import io.bullet.borer.Json
import munit.FunSuite

class JsonWebKeySpec extends FunSuite:

  test("encode/decode rsa"):
    val rsa: JsonWebKey = JsonWebKey.Rsa(
      keyId = KeyId("id1"),
      algorithm = "RS256",
      keyUse = KeyUse.Sign,
      modulus = "the-n",
      exponent = "the-e"
    )
    val jsonStr = Json.encode(rsa).toUtf8String
    val webDec = Json.decode(jsonStr.getBytes).to[JsonWebKey].value
    assertEquals(webDec, rsa)

  test("encode/decode ec"):
    val ec: JsonWebKey = JsonWebKey.Ec(
      keyId = KeyId("id1"),
      algorithm = "ECDS",
      keyUse = KeyUse.Sign,
      x = "the-x",
      y = "the-y",
      curve = Curve.P256
    )
    val jsonStr = Json.encode(ec).toUtf8String
    val webDec = Json.decode(jsonStr.getBytes).to[JsonWebKey].value
    assertEquals(webDec, ec)

  test("encode/decode okp"):
    val ec: JsonWebKey = JsonWebKey.Okp(
      keyId = KeyId("id1"),
      algorithm = "ECDS",
      keyUse = KeyUse.Sign,
      x = "the-x",
      curve = Curve.P256
    )
    val jsonStr = Json.encode(ec).toUtf8String
    val webDec = Json.decode(jsonStr.getBytes).to[JsonWebKey].value
    assertEquals(webDec, ec)

  test("unsupported key type"):
    val jsonStr =
      """{"kty":"XYZ","kid":"id1","alg":"RS256","use":"sig","n":"the-n","e":"the-e"}"""
    val dec = Json.decode(jsonStr.getBytes).to[JsonWebKey].valueEither
    assert(dec.isLeft)
