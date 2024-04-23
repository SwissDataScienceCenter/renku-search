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

package io.renku.queue.client

import cats.syntax.all.*

import io.renku.avro.codec.{AvroDecoder, AvroReader}
import io.renku.redis.client.MessageId
import io.renku.search.events.{
  DataContentType,
  EventMessage,
  MessageHeader,
  RenkuEventPayload
}
import org.apache.avro.Schema
import scodec.bits.ByteVector

final case class QueueHeaderMessage(
    id: MessageId,
    header: MessageHeader,
    payload: ByteVector
):
  def toMessage[A <: RenkuEventPayload](schema: Schema)(using
      AvroDecoder[A]
  ): Either[Throwable, EventMessage[A]] =
    decodePayload(schema).map(pl => EventMessage(header, schema, pl))

  def decodePayload[A: AvroDecoder](schema: Schema): Either[Throwable, Seq[A]] =
    decodePayload(AvroReader(schema))

  def decodePayload[A: AvroDecoder](avro: AvroReader): Either[Throwable, Seq[A]] =
    header.dataContentType match
      case DataContentType.Binary => Either.catchNonFatal(avro.read[A](payload))
      case DataContentType.Json   => Either.catchNonFatal(avro.readJson[A](payload))
