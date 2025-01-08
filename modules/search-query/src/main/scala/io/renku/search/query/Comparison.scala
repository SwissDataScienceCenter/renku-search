package io.renku.search.query

import io.bullet.borer.{Decoder, Encoder}

enum Comparison:
  case Is
  case LowerThan
  case GreaterThan

  private[query] def asString = this match
    case Is          => ":"
    case LowerThan   => "<"
    case GreaterThan => ">"

object Comparison:
  given Encoder[Comparison] = Encoder.forString.contramap(_.asString)
  given Decoder[Comparison] = Decoder.forString.mapEither(fromString)

  private[query] def fromString(str: String): Either[String, Comparison] =
    Comparison.values.find(_.asString == str).toRight(s"Invalid comparison: $str")

  private[query] def unsafeFromString(str: String): Comparison =
    fromString(str).fold(sys.error, identity)
