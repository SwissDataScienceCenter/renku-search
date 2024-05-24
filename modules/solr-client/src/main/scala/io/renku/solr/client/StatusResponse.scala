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
