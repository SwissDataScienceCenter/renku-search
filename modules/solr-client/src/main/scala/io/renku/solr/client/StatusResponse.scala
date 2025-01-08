package io.renku.solr.client

import java.time.Instant
import java.time.format.DateTimeParseException

import cats.syntax.all.*

import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs

final case class StatusResponse(
    responseHeader: ResponseHeader,
    status: Map[String, StatusResponse.CoreStatus] = Map.empty
)

object StatusResponse:
  private given Decoder[Instant] =
    Decoder.forString.mapEither { v =>
      Either
        .catchOnly[DateTimeParseException](Instant.parse(v))
        .leftMap(_.getMessage)
    }

  final case class IndexStatus(
      numDocs: Long,
      maxDoc: Long,
      version: Long,
      current: Boolean,
      segmentCount: Long,
      hasDeletions: Boolean,
      sizeInBytes: Long
  )

  object IndexStatus:
    given Decoder[IndexStatus] = MapBasedCodecs.deriveDecoder

  final case class CoreStatus(
      name: String,
      uptime: Long,
      startTime: Instant,
      index: IndexStatus
  )

  object CoreStatus:
    given Decoder[CoreStatus] = MapBasedCodecs.deriveDecoder

  given Decoder[StatusResponse] = MapBasedCodecs.deriveDecoder
