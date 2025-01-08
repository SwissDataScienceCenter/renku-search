package io.renku.search.http.borer

import io.bullet.borer.*
import org.http4s.*

trait Http4sJsonCodec:
  given Encoder[Uri] = Encoder.forString.contramap(_.renderString)
  given Decoder[Uri] =
    Decoder.forString.mapEither(s => Uri.fromString(s).left.map(_.getMessage))

object Http4sJsonCodec extends Http4sJsonCodec
