package io.renku.search.query.parse

import java.time.*

import cats.parse.{Numbers, Parser as P}

import io.renku.search.query.*

/** Allows parsing partial date-time strings, allowing to fill missing parts with either
  * lowest or highest possible values.
  */
object DateTimeParser {

  val colon: P[Unit] = P.char(':')
  val dash: P[Unit] = P.char('-')
  val T: P[Unit] = P.char('T')

  val utcOffset: P[ZoneId] =
    P.char('Z').as(ZoneOffset.UTC)

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

  val date = (year ~ (dash *> month).? ~ (dash *> dom).?).map { case ((y, m), d) =>
    PartialDateTime.Date(y, m, d)
  }

  val time = (hour ~ (colon *> minsec).? ~ (colon *> minsec).?).map { case ((h, m), s) =>
    PartialDateTime.Time(h, m, s)
  }

  val partialDateTime: P[PartialDateTime] =
    (date ~ (T *> time).? ~ utcOffset.?).map { case ((d, t), z) =>
      PartialDateTime(d, t, z)
    }

  val relativeDate: P[RelativeDate] =
    P.stringIn(RelativeDate.values.map(_.name).toSeq).map(RelativeDate.unsafeFromString)

  val dateCalc: P[DateTimeCalc] = {
    val ref: P[PartialDateTime | RelativeDate] = relativeDate | partialDateTime
    val sep = P.stringIn(Seq(DateTimeCalc.add, DateTimeCalc.sub, DateTimeCalc.range))
    val days =
      ((Numbers.nonZeroDigit ~ Numbers.digits0).string <* P.charIn("dD").void).map(s =>
        Period.ofDays(s.toInt)
      )
    (ref ~ sep ~ days).map { case ((date, op), amount) =>
      val p =
        if (op == DateTimeCalc.sub) amount.negated()
        else amount
      DateTimeCalc(date, p, op == DateTimeCalc.range)
    }
  }

  val dateTimeRef: P[DateTimeRef] =
    dateCalc.map(DateTimeRef.apply).backtrack |
      partialDateTime.map(DateTimeRef.apply) |
      relativeDate.map(DateTimeRef.apply)
}
