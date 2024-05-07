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

import io.renku.avro.codec.*
import io.renku.avro.codec.all.given
import io.renku.events.Header
import io.renku.search.model.Timestamp
import scodec.bits.ByteVector

final case class MessageHeader(
    source: MessageSource,
    dataContentType: DataContentType,
    schemaVersion: SchemaVersion,
    time: Timestamp,
    requestId: RequestId
):
  def withContentType(dt: DataContentType): MessageHeader = copy(dataContentType = dt)
  def withSchemaVersion(v: SchemaVersion): MessageHeader = copy(schemaVersion = v)

  def toAvro(payloadType: String): ByteVector =
    val h =
      Header(
        source.value,
        payloadType,
        dataContentType.mimeType,
        schemaVersion.name,
        time.toInstant,
        requestId.value
      )
    AvroWriter(Header.SCHEMA$).write(Seq(h))

object MessageHeader:
  def create[F[_]: Sync](
      src: MessageSource,
      ct: DataContentType,
      sv: SchemaVersion,
      reqId: RequestId
  ): F[MessageHeader] =
    Timestamp.now[F].map(ts => MessageHeader(src, ct, sv, ts, reqId))

  def fromByteVector(bv: ByteVector): Either[String, MessageHeader] =
    Either
      .catchNonFatal(AvroReader(Header.SCHEMA$).read[Header](bv))
      .leftMap(_.getMessage)
      .map(_.distinct.toList)
      .flatMap {
        case h :: Nil => Right(h)
        case Nil      => Left(s"No header record found in byte vector: $bv")
        case hs       => Left(s"More than one (${hs.size}) headers in: $bv")
      }
      .flatMap { h =>
        for
          ct <- DataContentType.fromMimeType(h.dataContentType)
          v <- SchemaVersion.fromString(h.schemaVersion)
          src = MessageSource(h.source)
          ts = Timestamp(h.time)
          rid = RequestId(h.requestId)
        yield MessageHeader(src, ct, v, ts, rid)
      }
