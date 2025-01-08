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
    msgType: MsgType,
    dataContentType: DataContentType,
    schemaVersion: SchemaVersion,
    time: Timestamp,
    requestId: RequestId
):
  def withContentType(dt: DataContentType): MessageHeader = copy(dataContentType = dt)
  def withSchemaVersion(v: SchemaVersion): MessageHeader = copy(schemaVersion = v)

  def toAvro: ByteVector =
    val h =
      Header(
        source.value,
        msgType.name,
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
      msgType: MsgType,
      ct: DataContentType,
      sv: SchemaVersion,
      reqId: RequestId
  ): F[MessageHeader] =
    Timestamp.now[F].map(ts => MessageHeader(src, msgType, ct, sv, ts, reqId))

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
          mst <- MsgType
            .fromString(h.`type`)
            .leftMap(err => DecodeFailure.FieldReadError("type", h.`type`, err))
          src = MessageSource(h.source)
          ts = Timestamp(h.time)
          rid = RequestId(h.requestId)
        yield MessageHeader(src, mst, ctReal, v, ts, rid)
      }
