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

import io.renku.events.{v1, v2}
import io.renku.search.events.*
import org.apache.avro.Schema

trait SchemaSelect:
  def select(header: MessageHeader): Schema

object SchemaSelect:
  def instance(f: MessageHeader => Schema): SchemaSelect =
    (h: MessageHeader) => f(h)

  def fromVersion(f: SchemaVersion => Schema): SchemaSelect =
    instance(h => f(h.schemaVersion))

  //hm, not sure about this…
  val projectRemoved: SchemaSelect =
    fromVersion {
      case SchemaVersion.V1 => v1.ProjectRemoved.SCHEMA$
      case SchemaVersion.V2 => v2.ProjectRemoved.SCHEMA$
    }
