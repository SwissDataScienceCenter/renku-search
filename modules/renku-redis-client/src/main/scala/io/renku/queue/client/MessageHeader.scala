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
import io.renku.events.v1.Header
import org.apache.avro.Schema

import java.time.Instant
import java.time.temporal.ChronoUnit
import cats.effect.Clock
import cats.Functor

final case class MessageHeader(
    source: MessageSource,
    payloadSchema: Schema,
    dataContentType: DataContentType,
    schemaVersion: SchemaVersion,
    time: CreationTime,
    requestId: RequestId
):
  def toSchemaHeader(p: Any): Header =
    Header(
      source.value,
      p.getClass.getName,
      dataContentType.mimeType,
      schemaVersion.value,
      time.value,
      requestId.value
    )

object MessageHeader:
  def apply(
      source: MessageSource,
      payloadSchema: Schema,
      dataContentType: DataContentType,
      schemaVersion: SchemaVersion,
      requestId: RequestId
  ): MessageHeader =
    MessageHeader(
      source,
      payloadSchema,
      dataContentType,
      schemaVersion,
      CreationTime.now,
      requestId
    )

opaque type MessageSource = String
object MessageSource:
  def apply(v: String): MessageSource = v
  extension (self: MessageSource) def value: String = self

opaque type SchemaVersion = String
object SchemaVersion:
  val V1: SchemaVersion = "V1"
  def apply(v: String): SchemaVersion = v
  extension (self: SchemaVersion) def value: String = self

opaque type CreationTime = Instant
object CreationTime:
  def apply(v: Instant): CreationTime = v
  def now: CreationTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  def nowF[F[_]: Clock: Functor]: F[CreationTime] =
    Clock[F].realTimeInstant.map(_.truncatedTo(ChronoUnit.MILLIS))

  extension (self: CreationTime) def value: Instant = self

opaque type RequestId = String
object RequestId:
  def apply(v: String): RequestId = v
  extension (self: RequestId) def value: String = self
