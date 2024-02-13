package io.renku.search.query

import cats.parse.{Numbers, Parser as P}

import java.time.*

/** Allows parsing partial date-time strings, filling missing parts with either lowest or
  * highest possible values.
  */
final class DateTimeParser(zoneId: ZoneId) {

  val colon: P[Unit] = P.char(':')
  val dash: P[Unit] = P.char('-')
  val T: P[Unit] = P.char('T')

  val zoneOffset: P[ZoneId] =
    P.char('Z').as(ZoneOffset.UTC)

  val optZone =
    zoneOffset.orElse(P.pure(zoneId))

  val year: P[Int] =
    (Numbers.nonZeroDigit ~ Numbers.digit.repExactlyAs[String](3)).string.map(_.toInt)

  val month: P[Int] =
    Numbers.digit
      .rep(1, 2)
      .string
      .map(_.toInt)
      .filter(n => n >= 1 && n <= 12)
      .withContext("Month not in range 1-12")

  val dom: P[Int] =
    Numbers.digit
      .rep(1, 2)
      .string
      .map(_.toInt)
      .filter(n => n >= 1 && n <= 31)
      .withContext("Day not in range 1-31")

  val hour: P[Int] =
    Numbers.digit
      .rep(1, 2)
      .string
      .map(_.toInt)
      .filter(n => n >= 0 && n <= 23)
      .withContext("Day not in range 0-23")

  val minsec: P[Int] =
    Numbers.digit
      .rep(1, 2)
      .string
      .map(_.toInt)
      .filter(n => n >= 0 && n <= 59)
      .withContext("Minute/second not in range 0-59")

  def withMonth(default: Int) =
    (dash *> month).orElse(P.pure(default))

  def withDay(default: Int) =
    (dash *> dom).orElse(P.pure(default))

  val dateMin =
    (year ~ withMonth(1) ~ withDay(1)).map { case ((y, m), d) =>
      LocalDate.of(y, m, d)
    }

  val dateMax =
    (year ~ withMonth(12) ~ (dash *> dom).?).map { case ((y, m), dopt) =>
      val ym = YearMonth.of(y, m)
      val md = ym.atEndOfMonth().getDayOfMonth
      LocalDate.of(y, m, dopt.getOrElse(md))
    }

  def withMinSec(default: Int) =
    (colon *> minsec).orElse(P.pure(default))

  val timeMin =
    (hour ~ withMinSec(0) ~ withMinSec(0))
      .map { case ((h, m), s) =>
        LocalTime.of(h, m, s)
      }

  val timeMax =
    (hour ~ withMinSec(59) ~ withMinSec(59))
      .map { case ((h, m), s) =>
        LocalTime.of(h, m, s)
      }

  def withTime(p: P[LocalTime], default: LocalTime) =
    (T *> p).orElse(P.pure(default))

  val offsetDateTimeMin: P[ZonedDateTime] =
    (dateMin ~ withTime(timeMin, LocalTime.MIDNIGHT) ~ optZone)
      .map { case ((ld, lt), zid) =>
        ZonedDateTime.of(ld, lt, zid)
      }

  val offsetDateTimeMax: P[ZonedDateTime] =
    (dateMax ~ withTime(timeMax, LocalTime.MIDNIGHT.minusSeconds(1)) ~ optZone)
      .map { case ((ld, lt), zid) =>
        ZonedDateTime.of(ld, lt, zid)
      }

  val instantMin: P[Instant] = offsetDateTimeMin.map(_.toInstant)
  val instantMax: P[Instant] = offsetDateTimeMax.map(_.toInstant)
}

object DateTimeParser:
  val utc: DateTimeParser = new DateTimeParser(ZoneOffset.UTC)
