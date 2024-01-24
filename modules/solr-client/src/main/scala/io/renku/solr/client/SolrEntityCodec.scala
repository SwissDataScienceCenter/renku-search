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

package io.renku.solr.client

import cats.data.EitherT
import cats.effect.Concurrent
import cats.syntax.all.*
import fs2.Chunk
import io.renku.avro.codec.json.{AvroJsonDecoder, AvroJsonEncoder}
import org.http4s.headers.`Content-Type`
import org.http4s.{EntityDecoder, EntityEncoder, MalformedMessageBodyFailure, MediaType}
import scodec.bits.ByteVector

trait SolrEntityCodec {

  given jsonEntityEncoder[F[_], A](using enc: AvroJsonEncoder[A]): EntityEncoder[F, A] =
    EntityEncoder.simple(`Content-Type`(MediaType.application.json))(a =>
      val bytes = enc.encode(a)
      scribe.trace(s"Solr request payload: ${bytes.decodeUtf8Lenient}")
      Chunk.byteVector(bytes)
    )

  given jsonEntityDecoder[F[_]: Concurrent, A](using
      decoder: AvroJsonDecoder[A]
  ): EntityDecoder[F, A] =
    EntityDecoder.decodeBy(MediaType.application.json) { m =>
      EitherT(
        m.body.chunks
          .map(_.toByteVector)
          .compile
          .fold(ByteVector.empty)(_ ++ _)
          .map(decoder.decode)
          .map(_.leftMap(err => MalformedMessageBodyFailure(err)))
      )
    }
}

object SolrEntityCodec extends SolrEntityCodec
