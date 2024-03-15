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

package io.renku.search.jwt

import munit.FunSuite
import pdi.jwt.Jwt
import pdi.jwt.JwtAlgorithm

class JwtBorerSpec extends FunSuite:

  val secret = new javax.crypto.spec.SecretKeySpec("abcdefg".getBytes, "HS256")
  val exampleToken =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJteS11c2VyLWlkIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.d7F1v9sfcQzVrEGXXhJoGukbfXhm3zKn0fUyvFAMzm0"

  val regexDecode = Jwt.decodeAll(exampleToken, secret).get

  test("decode"):
    val (header, claim, _) = JwtBorer.decodeAll(exampleToken, secret).get
    assertEquals(header.algorithm, Some(JwtAlgorithm.HS256))
    assertEquals(header.typ, Some("JWT"))
    assertEquals(header.keyId, None)
    assertEquals(header.contentType, None)

    assertEquals(claim.subject, Some("my-user-id"))
    assertEquals(claim.issuedAt, Some(1516239022L))
    assertEquals(header, regexDecode._1)
    assertEquals(claim, regexDecode._2.withContent("{}"))

  test("decode without secret"):
    val (header, claim, _) = JwtBorer.decodeAllNoSignatureCheck(exampleToken).get
    val claim2 = JwtBorer.decodeNoSignatureCheck(exampleToken).get
    assertEquals(claim, claim2)
    assertEquals(header, regexDecode._1)
    assertEquals(claim, regexDecode._2.withContent("{}"))
