package io.renku.search.http.borer

import cats.data.EitherT
import cats.effect.*
import cats.syntax.all.*
import fs2.Chunk
import io.bullet.borer.*
import org.http4s.*
import org.http4s.headers.*

object BorerEntities:

  def decodeEntityJson[F[_]: Async, A: Decoder]: EntityDecoder[F, A] =
    EntityDecoder.decodeBy(MediaType.application.json)(decodeJson)

  def decodeEntityCbor[F[_]: Async, A: Decoder]: EntityDecoder[F, A] =
    EntityDecoder.decodeBy(MediaType.application.cbor)(decodeCbor)

  def decodeJson[F[_]: Async, A: Decoder](media: Media[F]): DecodeResult[F, A] =
    EitherT(StreamProvider(media.body).flatMap { implicit input =>
      for {
        res <- Async[F].delay(Json.decode(input).to[A].valueEither)
        txt <- if (res.isLeft) media.bodyText.compile.string else Async[F].pure("")
      } yield res.left.map(BorerDecodeFailure(txt, _))
    })

  def decodeCbor[F[_]: Async, A: Decoder](media: Media[F]): DecodeResult[F, A] =
    EitherT(StreamProvider(media.body).flatMap { implicit input =>
      for {
        res <- Async[F].delay(Cbor.decode(input).to[A].valueEither)
      } yield res.left.map(BorerDecodeFailure("<not available>", _))
    })

  def encodeEntityJson[F[_], A: Encoder]: EntityEncoder[F, A] =
    EntityEncoder.simple(`Content-Type`(MediaType.application.json))(a =>
      Chunk.array(Json.encode(a).toByteArray)
    )

  def encodeEntityCbor[F[_], A: Encoder]: EntityEncoder[F, A] =
    EntityEncoder.simple(`Content-Type`(MediaType.application.cbor))(a =>
      Chunk.array(Cbor.encode(a).toByteArray)
    )
