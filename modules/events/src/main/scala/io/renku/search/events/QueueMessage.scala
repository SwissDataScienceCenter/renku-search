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

package io.renku.search.events

import cats.syntax.all.*

import io.renku.avro.codec.{AvroDecoder, AvroReader}
import org.apache.avro.Schema
import scodec.bits.ByteVector

final case class QueueMessage(id: MessageId, header: MessageHeader, payload: ByteVector):

  def decodePayload[A: AvroDecoder](schema: Schema): Either[DecodeFailure, Seq[A]] =
    decodePayload(AvroReader(schema))

  def toMessage[A: AvroDecoder](schema: Schema): Either[DecodeFailure, EventMessage[A]] =
    decodePayload(schema).map(p => EventMessage(id, header, schema, p))

  private def decodePayload[A: AvroDecoder](
      avro: AvroReader
  ): Either[DecodeFailure, Seq[A]] =
    header.dataContentType match
      case DataContentType.Binary =>
        Either
          .catchNonFatal(avro.read[A](payload))
          .leftMap(DecodeFailure.AvroReadFailure.apply)
      case DataContentType.Json =>
        Either
          .catchNonFatal(avro.readJson[A](payload))
          .leftMap(DecodeFailure.AvroReadFailure.apply)
