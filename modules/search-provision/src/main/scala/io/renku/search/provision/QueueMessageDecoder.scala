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

package io.renku.search.provision

import cats.MonadThrow
import cats.syntax.all.*
import io.renku.avro.codec.{AvroDecoder, AvroReader}
import io.renku.queue.client.{DataContentType, QueueMessage}
import org.apache.avro.Schema

private class QueueMessageDecoder[F[_]: MonadThrow, A](schema: Schema)(using
    AvroDecoder[A]
):
  private val avro = AvroReader(schema)

  def decodeMessage(message: QueueMessage): F[Seq[A]] =
    findContentType.andThenF(decodePayload(message))(message)

  private def findContentType(message: QueueMessage): F[DataContentType] =
    MonadThrow[F]
      .fromEither(DataContentType.from(message.header.dataContentType))

  private def decodePayload(message: QueueMessage): DataContentType => F[Seq[A]] = {
    case DataContentType.Binary => catchNonFatal(avro.read[A](message.payload))
    case DataContentType.Json   => catchNonFatal(avro.readJson[A](message.payload))
  }

  private def catchNonFatal(f: => Seq[A]): F[Seq[A]] =
    MonadThrow[F].catchNonFatal(f)
