package io.renku.search.query

import java.time.Period

final case class DateTimeCalc(
    ref: PartialDateTime | RelativeDate,
    amount: Period,
    range: Boolean
):
  def asString: String =
    val period = amount.getDays.abs
    val sep =
      if (range) DateTimeCalc.range
      else if (amount.isNegative) DateTimeCalc.sub
      else DateTimeCalc.add
    ref match
      case d: PartialDateTime =>
        s"${d.asString}$sep${period}d"

      case d: RelativeDate =>
        s"${d.name}$sep${period}d"

object DateTimeCalc:
  private[query] val add: String = "+"
  private[query] val sub: String = "-"
  private[query] val range: String = "/"
