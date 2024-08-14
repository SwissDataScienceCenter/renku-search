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

import cats.effect.Sync
import cats.syntax.all.*

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.AvroWriter
import org.apache.avro.Schema
import scodec.bits.ByteVector

final case class EventMessage[P](
    id: MessageId,
    header: MessageHeader,
    payloadSchema: Schema,
    payload: Seq[P]
):
  private lazy val payloadWriter = AvroWriter(payloadSchema)

  def toAvro(using AvroEncoder[P]): EventMessage.AvroPayload =
    val h = header.toAvro
    val b = header.dataContentType match
      case DataContentType.Binary => payloadWriter.write(payload)
      case DataContentType.Json   => payloadWriter.writeJson(payload)
    EventMessage.AvroPayload(h, b)

  def map[B](f: P => B): EventMessage[B] =
    EventMessage(id, header, payloadSchema, payload.map(f))

  def withPayload(pl: Seq[P]): EventMessage[P] = copy(payload = pl)

  def modifyHeader(f: MessageHeader => MessageHeader): EventMessage[P] =
    copy(header = f(header))

object EventMessage:
  def create[F[_]: Sync, A <: RenkuEventPayload](
      src: MessageSource,
      ct: DataContentType,
      reqId: RequestId,
      payload: A
  )(using mt: MsgType.Mapping[A]): F[EventMessage[A]] =
    (
      MessageId.random[F],
      MessageHeader.create(src, mt.msgType, ct, payload.version.head, reqId)
    )
      .mapN((id, h) => EventMessage(id, h, payload.schema, Seq(payload)))

  final case class AvroPayload(header: ByteVector, payload: ByteVector)
