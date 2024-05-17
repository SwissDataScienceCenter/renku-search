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
