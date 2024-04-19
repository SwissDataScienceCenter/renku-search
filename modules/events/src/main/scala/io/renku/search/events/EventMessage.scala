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

import org.apache.avro.Schema
import io.renku.avro.codec.AvroEncoder
import scodec.bits.ByteVector
import io.renku.avro.codec.AvroWriter

final case class EventMessage[P <: RenkuEventPayload](
    /* id: MessageId? */
    header: MessageHeader,
    payloadSchema: Schema,
    payload: P
):

  private val payloadWriter = AvroWriter(payloadSchema)

  def toAvro(v: SchemaVersion)(using AvroEncoder[P]): EventMessage.AvroPayload =
    val h = header.toAvro(v, payload.getClass.getName)
    val b = payloadWriter.write(Seq(payload))
    EventMessage.AvroPayload(h, b)

object EventMessage:

  final case class AvroPayload(header: ByteVector, payload: ByteVector)
