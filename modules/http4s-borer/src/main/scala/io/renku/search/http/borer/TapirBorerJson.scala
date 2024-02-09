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

import io.bullet.borer.{Borer, Decoder, Encoder, Json}
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, EndpointIO, RawBodyType, Schema}

trait TapirBorerJson:

  def borerJsonBody[T: Encoder: Decoder: Schema]: EndpointIO.Body[Array[Byte], T] =
    jsonBodyAnyFormat(borerCodec[T])

  private type BorerJsonCodec[T] = Codec[Array[Byte], T, CodecFormat.Json]

  private def jsonBodyAnyFormat[T](
      codec: BorerJsonCodec[T]
  ): EndpointIO.Body[Array[Byte], T] =
    EndpointIO.Body(RawBodyType.ByteArrayBody, codec, EndpointIO.Info.empty)

  private def borerCodec[T: Encoder: Decoder: Schema]: BorerJsonCodec[T] =
    new Codec[Array[Byte], T, CodecFormat.Json]:

      override def rawDecode(l: Array[Byte]): DecodeResult[T] =
        Json
          .decode(l)
          .to[T]
          .valueEither
          .fold(borerErrorToTapir, Value.apply)

      private def borerErrorToTapir[IP](be: Borer.Error[IP]): Error =
        Error(
          original = be.getMessage,
          error = JsonDecodeException(
            errors = List(JsonError(be.getMessage, Nil)),
            underlying = be
          )
        )

      override def encode(h: T): Array[Byte] =
        Json.encode(h).toByteArray

      override lazy val schema: Schema[T] = implicitly[Schema[T]]

      override lazy val format: CodecFormat.Json = CodecFormat.Json()
