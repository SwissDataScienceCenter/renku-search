package io.renku.search.http.borer

import cats.effect.Async
import io.bullet.borer.{Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder}

trait BorerEntityJsonCodec:
  given [F[_]: Async, A: Decoder]: EntityDecoder[F, A] =
    BorerEntities.decodeEntityJson[F, A]

  given [F[_], A: Encoder]: EntityEncoder[F, A] =
    BorerEntities.encodeEntityJson[F, A]

object BorerEntityJsonCodec extends BorerEntityJsonCodec
