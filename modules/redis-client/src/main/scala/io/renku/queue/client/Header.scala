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

import java.time.Instant

final case class Header(
    source: Option[MessageSource],
    messageType: Option[MessageType],
    dataContentType: DataContentType,
    schemaVersion: Option[SchemaVersion],
    time: Option[CreationTime],
    requestId: Option[RequestId]
)

object Header:
  def apply(contentType: DataContentType): Header =
    Header(
      source = None,
      messageType = None,
      dataContentType = contentType,
      schemaVersion = None,
      time = None,
      requestId = None
    )

opaque type MessageSource = String
object MessageSource:
  def apply(v: String): MessageSource = v
  extension (self: MessageSource) def value: String = self

opaque type MessageType = String
object MessageType:
  def apply(v: String): MessageType = v
  extension (self: MessageType) def value: String = self

enum DataContentType(val mimeType: String):
  lazy val name: String = productPrefix
  case Binary extends DataContentType("application/avro+binary")
  case Json extends DataContentType("application/avro+json")

object DataContentType:
  def from(mimeType: String): Either[Throwable, DataContentType] =
    DataContentType.values.toList
      .find(_.mimeType == mimeType)
      .toRight(
        new IllegalArgumentException(s"'$mimeType' not a valid 'DataContentType' value")
      )

opaque type SchemaVersion = String
object SchemaVersion:
  def apply(v: String): SchemaVersion = v
  extension (self: SchemaVersion) def value: String = self

opaque type CreationTime = Instant
object CreationTime:
  def apply(v: Instant): CreationTime = v
  extension (self: CreationTime) def value: Instant = self

opaque type RequestId = String
object RequestId:
  def apply(v: String): RequestId = v
  extension (self: RequestId) def value: String = self
