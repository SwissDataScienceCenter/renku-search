package io.renku.search.jwt

import scala.io.Source

import io.bullet.borer.Json
import munit.FunSuite

class RenkuTokenSpec extends FunSuite:

  test("decode jwt payload"):
    val jsonStr = Source.fromResource("jwt1.json").mkString
    val decoded = Json.decode(jsonStr.getBytes).to[RenkuToken].value
    assertEquals(decoded.subject, Some("48c85c75-b407-4259-b06b-a611e71df5f0"))
    assert(decoded.isAdmin == false)

  test("decode jwt payload (admin)"):
    val jsonStr = Source.fromResource("jwt2.json").mkString
    val decoded = Json.decode(jsonStr.getBytes).to[RenkuToken].value
    assertEquals(decoded.subject, Some("48c85c75-b407-4259-b06b-a611e71df5f0"))
    assert(decoded.isAdmin == true)
