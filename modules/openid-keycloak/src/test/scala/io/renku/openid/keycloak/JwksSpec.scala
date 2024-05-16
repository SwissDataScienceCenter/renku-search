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

import munit.FunSuite
import scala.io.Source
import io.bullet.borer.Json
import io.renku.search.jwt.JwtBorer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class JwksSpec extends FunSuite:

  val jwksJson = Source.fromResource("jwks.json").mkString
  lazy val jwks = Json.decode(jwksJson.getBytes).to[Jwks].value

  // valid until 2024-05-15T14:47:26Z
  val jwToken = Source.fromResource("jwt-token1").mkString
  val fixedClock = new Clock {
    val time = Instant.parse("2024-05-15T13:47:26Z")
    def instant(): Instant = time
    def getZone(): ZoneId = ZoneId.of("UTC")
    override def withZone(zone: ZoneId): Clock = this
  }

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
    println(JwtBorer(fixedClock).decodeAllNoSignatureCheck(jwToken))
    assert(result.isSuccess)
    assertEquals(
      result.get._2.issuer,
      Some("https://ci-renku-3622.dev.renku.ch/auth/realms/Renku")
    )
