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

trait DecodeFailure extends RuntimeException

object DecodeFailure:
  abstract private[events] class NoStackTrace(message: String)
      extends RuntimeException(message)
      with DecodeFailure:
    override def fillInStackTrace(): Throwable = this

  final case class AvroReadFailure(cause: Throwable)
      extends RuntimeException(cause)
      with DecodeFailure

  final case class VersionNotSupported(id: MessageId, header: MessageHeader)
      extends NoStackTrace(
        s"Version ${header.schemaVersion} not supported for payload in message $id (header: $header)"
      )
