package io.renku.search.jwt

import java.time.Instant

import munit.FunSuite
import pdi.jwt.Jwt
import pdi.jwt.JwtAlgorithm

class JwtBorerSpec extends FunSuite:

  val secret = new javax.crypto.spec.SecretKeySpec("abcdefg".getBytes, "HS256")
  val exampleToken =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJteS11c2VyLWlkIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.d7F1v9sfcQzVrEGXXhJoGukbfXhm3zKn0fUyvFAMzm0"

  val regexDecode = Jwt.decodeAll(exampleToken, secret).get

  val expectClaim = RenkuToken(
    issuedAt = Some(Instant.ofEpochSecond(1516239022L)),
    subject = Some("my-user-id"),
    name = Some("John Doe")
  )

  test("decode"):
    val (header, claim, _) = JwtBorer.decodeAll(exampleToken, secret).get
    assertEquals(header.algorithm, Some(JwtAlgorithm.HS256))
    assertEquals(header.typ, Some("JWT"))
    assertEquals(header.keyId, None)
    assertEquals(header.contentType, None)

    assertEquals(claim.subject, expectClaim.subject)
    assertEquals(claim.issuedAt, expectClaim.issuedAt)
    assertEquals(header, regexDecode._1)
    assertEquals(claim, expectClaim)

  test("decode without secret"):
    val (header, claim, _) = JwtBorer.decodeAllNoSignatureCheck(exampleToken).get
    val claim2 = JwtBorer.decodeNoSignatureCheck(exampleToken).get
    assertEquals(claim, claim2)
    assertEquals(header, regexDecode._1)
    assertEquals(claim, expectClaim)
