package io.renku.json.codecs

import java.time.Instant

import io.bullet.borer.Encoder

trait DateTimeEncoders:
  given Encoder[Instant] = DateTimeEncoders.forInstant

object DateTimeEncoders:
  val forInstant: Encoder[Instant] = Encoder.forString.contramap[Instant](_.toString)
