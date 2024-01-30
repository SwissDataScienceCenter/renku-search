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

package io.renku.search.http.avro

import cats.data.EitherT
import cats.effect.Async
import cats.syntax.all.*
import fs2.Chunk
import io.renku.avro.codec.json.{AvroJsonDecoder, AvroJsonEncoder}
import org.http4s.*
import org.http4s.MediaType.application
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector

object AvroEntityCodec extends AvroEntityCodec:
  export Implicits.given

trait AvroEntityCodec:

  def decodeEntity[F[_]: Async, A: AvroJsonDecoder]: EntityDecoder[F, A] =
    EntityDecoder.decodeBy(MediaType.application.json)(decodeJson)

  private def decodeJson[F[_]: Async, A: AvroJsonDecoder](
      media: Media[F]
  ): DecodeResult[F, A] =
    EitherT {
      media.body.compile
        .to(ByteVector)
        .map(decodeAvro)
    }

  private def decodeAvro[A: AvroJsonDecoder]: ByteVector => Either[DecodeFailure, A] =
    AvroJsonDecoder[A]
      .decode(_)
      .leftMap(err =>
        MalformedMessageBodyFailure(s"Cannot decode Json Avro message: $err")
      )

  def encodeEntity[F[_], A: AvroJsonEncoder]: EntityEncoder[F, A] =
    EntityEncoder.simple(`Content-Type`(application.json))(a =>
      Chunk.byteVector(AvroJsonEncoder[A].encode(a))
    )

  trait Implicits:

    given entityDecoder[F[_]: Async, A: AvroJsonDecoder]: EntityDecoder[F, A] =
      decodeEntity[F, A]

    given entityEncoder[F[_], A: AvroJsonEncoder]: EntityEncoder[F, A] =
      encodeEntity[F, A]

  object Implicits extends Implicits
