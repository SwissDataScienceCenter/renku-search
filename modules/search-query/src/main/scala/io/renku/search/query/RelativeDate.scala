package io.renku.search.query

enum RelativeDate:
  case Today
  case Yesterday

  val name: String = productPrefix.toLowerCase

object RelativeDate:
  def fromString(str: String): Either[String, RelativeDate] =
    RelativeDate.values
      .find(_.name.equalsIgnoreCase(str))
      .toRight(s"Invalid relative date-time: $str")

  def unsafeFromString(str: String): RelativeDate =
    fromString(str).fold(sys.error, identity)
