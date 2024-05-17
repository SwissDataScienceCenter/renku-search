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
