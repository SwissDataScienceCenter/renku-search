package io.renku.search.query.parse

import java.time.temporal.ChronoUnit
import java.time.{Instant, Period, ZoneOffset}

import io.renku.search.query.{DateTimeCalc, PartialDateTime}
import munit.FunSuite

class DateTimeParserSpec extends FunSuite with ParserSuite {
  val utc = ZoneOffset.UTC

  test("PartialDate: current time") {
    val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    assertEquals(
      DateTimeParser.partialDateTime.run(now.toString).instantMin(utc),
      now
    )
    assertEquals(
      DateTimeParser.partialDateTime.run(now.toString).instantMax(utc),
      now
    )
  }

  test("PartialDate: no time") {
    assertEquals(
      DateTimeParser.partialDateTime.run("2024-02"),
      PartialDateTime(PartialDateTime.Date(2024, Some(2)))
    )
    assertEquals(
      DateTimeParser.partialDateTime.run("2023-02-14"),
      PartialDateTime(PartialDateTime.Date(2023, Some(2), Some(14)))
    )
  }

  test("DateCalc") {
    val p = DateTimeParser.dateCalc
    assertEquals(
      p.run("2023-02-15-10d"),
      DateTimeCalc(
        PartialDateTime.unsafeFromString("2023-02-15"),
        Period.ofDays(-10),
        false
      )
    )
    assertEquals(
      p.run("2023-02+10d"),
      DateTimeCalc(
        PartialDateTime.unsafeFromString("2023-02"),
        Period.ofDays(10),
        false
      )
    )
    assertEquals(
      p.run("2023-02/10d"),
      DateTimeCalc(
        PartialDateTime.unsafeFromString("2023-02"),
        Period.ofDays(10),
        true
      )
    )
  }
}
