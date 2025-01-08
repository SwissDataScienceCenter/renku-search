package io.renku.search.http.borer

import cats.effect.Async
import io.bullet.borer.{Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder}

trait BorerEntityCborCodec:
  given [F[_]: Async, A: Decoder]: EntityDecoder[F, A] =
    BorerEntities.decodeEntityCbor[F, A]

  given [F[_], A: Encoder]: EntityEncoder[F, A] =
    BorerEntities.encodeEntityCbor[F, A]

object BorerEntityCborCodec extends BorerEntityCborCodec
