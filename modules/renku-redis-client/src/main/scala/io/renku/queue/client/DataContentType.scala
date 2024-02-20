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
