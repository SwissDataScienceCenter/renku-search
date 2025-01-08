package io.renku.search.query

import java.time.{Instant, ZoneOffset}

import munit.FunSuite

class PartialDateTimeSpec extends FunSuite {
  val utc = ZoneOffset.UTC

  test("minimum") {
    assertEquals(
      PartialDateTime.unsafeFromString("2023-01").instantMin(utc),
      Instant.parse("2023-01-01T00:00:00Z")
    )
  }

  test("leap year") {
    assertEquals(
      PartialDateTime
        .unsafeFromString("2024-02")
        .instantMax(utc),
      Instant.parse("2024-02-29T23:59:59Z")
    )
    assertEquals(
      PartialDateTime
        .unsafeFromString("2023-02")
        .instantMax(utc),
      Instant.parse("2023-02-28T23:59:59Z")
    )
  }
}
