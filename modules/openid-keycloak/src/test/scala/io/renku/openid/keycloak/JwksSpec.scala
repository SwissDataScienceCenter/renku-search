package io.renku.openid.keycloak

import io.renku.search.TestClock
import io.renku.search.jwt.JwtBorer
import munit.FunSuite

class JwksSpec extends FunSuite with JwtResources:
  test("parse jwks json"):
    assertEquals(jwks.keys.size, 2)
    assertEquals(
      jwks.keys.head.keyId,
      KeyId("Pg4RoLW9CNX6Z460K1vCjBckCnme3WV24D6i65KjsBU")
    )
    assertEquals(
      jwks.keys(1).keyId,
      KeyId("AHe0fpbn1nQtOa5O8FxRKnJ6Ocm76R2PYtAXvoLPvf4")
    )

  test("get public key"):
    jwks.keys.foreach {
      case k: JsonWebKey.Rsa =>
        assert(k.toPublicKey.isRight)
      case k: JsonWebKey.Ec =>
        assert(k.toPublicKey.isRight)
      case k: JsonWebKey.Okp =>
        assert(k.toPublicKey.isLeft)
    }

  test("validation rsa"):
    val header = JwtBorer.readHeader(jwToken).fold(throw _, identity)
    val pubKey = jwks.findPublicKey(header).fold(throw _, identity)
    val result = JwtBorer(fixedClock).decodeAll(jwToken, pubKey)
    assert(result.isSuccess)
    assertEquals(
      result.get._2.issuer,
      Some("https://ci-renku-3622.dev.renku.ch/auth/realms/Renku")
    )

  test("validation rsa in IO"):
    import cats.effect.IO
    import cats.effect.unsafe.implicits.*

    val clock = TestClock.fixedAt(jwTokenValidTime)
    val result = jwks.validate[IO](clock)(jwToken).unsafeRunSync()
    assert(result.isRight)
    assertEquals(
      result.fold(throw _, identity).issuer,
      Some("https://ci-renku-3622.dev.renku.ch/auth/realms/Renku")
    )
