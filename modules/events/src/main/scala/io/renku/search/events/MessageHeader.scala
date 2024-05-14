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

import cats.data.NonEmptyList
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
    dataContentType match
      case DataContentType.Binary => AvroWriter(Header.SCHEMA$).write(Seq(h))
      case DataContentType.Json   => AvroWriter(Header.SCHEMA$).writeJson(Seq(h))

object MessageHeader:
  def create[F[_]: Sync](
      src: MessageSource,
      ct: DataContentType,
      sv: SchemaVersion,
      reqId: RequestId
  ): F[MessageHeader] =
    Timestamp.now[F].map(ts => MessageHeader(src, ct, sv, ts, reqId))

  private def readJsonOrBinary(
      bv: ByteVector
  ): Either[DecodeFailure, (DataContentType, List[Header])] =
    val reader = AvroReader(Header.SCHEMA$)
    Either.catchNonFatal(reader.readJson[Header](bv)) match
      case Right(r) => Right(DataContentType.Json -> r.distinct.toList)
      case Left(exb) =>
        Either
          .catchNonFatal(reader.read[Header](bv))
          .map(r => DataContentType.Binary -> r.distinct.toList)
          .leftMap { exj =>
            DecodeFailure.HeaderReadError(bv, exb, exj)
          }

  private def logDataContentType(
      headerCt: DataContentType,
      decoded: DataContentType
  ): DataContentType =
    if (headerCt != decoded) {
      scribe.debug(
        s"ContentType ($headerCt) used for decoding the header is not same as advertised in the header ($decoded)! Choose the advertised format in the header to continue ($decoded)."
      )
    }
    decoded

  def fromByteVector(bv: ByteVector): Either[DecodeFailure, MessageHeader] =
    readJsonOrBinary(bv)
      .flatMap {
        case (ct, h :: Nil) => Right(ct -> h)
        case (_, Nil)       => Left(DecodeFailure.NoHeaderRecord(bv))
        case (ct, hs) =>
          Left(DecodeFailure.MultipleHeaderRecords(bv, NonEmptyList.fromListUnsafe(hs)))
      }
      .flatMap { case (headerCt, h) =>
        for
          ct <- DataContentType
            .fromMimeType(h.dataContentType)
            .leftMap(err =>
              DecodeFailure.FieldReadError("dataContentType", h.dataContentType, err)
            )
          ctReal = logDataContentType(headerCt, ct)
          v <- SchemaVersion
            .fromString(h.schemaVersion)
            .leftMap(err =>
              DecodeFailure.FieldReadError("schemaVersion", h.schemaVersion, err)
            )
          src = MessageSource(h.source)
          ts = Timestamp(h.time)
          rid = RequestId(h.requestId)
        yield MessageHeader(src, ctReal, v, ts, rid)
      }
