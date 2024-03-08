/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
