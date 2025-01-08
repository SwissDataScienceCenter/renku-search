package io.renku.search.query

import java.time.Instant
import java.time.Period
import java.time.ZoneId

import cats.syntax.all.*

import munit.FunSuite

class DateTimeRefSpec extends FunSuite:

  val refDate: Instant = Instant.parse("2024-02-27T15:34:55Z")
  val utc: ZoneId = ZoneId.of("UTC")

  test("resolve relative dates") {
    assertEquals(
      RelativeDate.Today.resolve,
      (refDate, None)
    )
    assertEquals(
      DateTimeRef.Relative(RelativeDate.Yesterday).resolve(refDate, utc),
      (refDate.atZone(utc).minusDays(1).toInstant(), None)
    )
  }

  test("resolve partial date") {
    val may = PartialDateTime.unsafeFromString("2023-05")
    assertEquals(
      may.resolve,
      (may.instantMin(utc), may.instantMax(utc).some)
    )
    val exact = PartialDateTime.fromInstant(Instant.EPOCH)
    assertEquals(exact.resolve, (exact.instantMin(utc), None))
  }

  test("resolve date calc") {
    val may = PartialDateTime.unsafeFromString("2023-05")
    val calc1 = DateTimeCalc(may, Period.ofDays(5), false)
    assertEquals(
      calc1.resolve,
      (may.instantMin(utc).atZone(utc).plusDays(5).toInstant(), None)
    )

    val calc2 = DateTimeCalc(may, Period.ofDays(-5), false)
    assertEquals(
      calc2.resolve,
      (may.instantMin(utc).atZone(utc).minusDays(5).toInstant(), None)
    )

    val range = DateTimeCalc(may, Period.ofDays(5), true)
    assertEquals(
      range.resolve,
      (
        may.instantMin(utc).atZone(utc).minusDays(5).toInstant(),
        may.instantMin(utc).atZone(utc).plusDays(5).toInstant().some
      )
    )
  }

  extension (r: RelativeDate) def resolve = DateTimeRef(r).resolve(refDate, utc)
  extension (d: PartialDateTime) def resolve = DateTimeRef(d).resolve(refDate, utc)
  extension (r: DateTimeCalc) def resolve = DateTimeRef(r).resolve(refDate, utc)
