package io.renku.json.codecs

import java.time.Instant
import java.time.format.DateTimeParseException

import cats.syntax.all.*

import io.bullet.borer.Decoder
import io.bullet.borer.Decoder.*

trait DateTimeDecoders:
  given Decoder[Instant] = DateTimeDecoders.forInstant

object DateTimeDecoders:

  val forInstant: Decoder[Instant] =
    Decoder.forString.mapEither { v =>
      Either
        .catchOnly[DateTimeParseException](Instant.parse(v))
        .leftMap(_.getMessage)
    }
