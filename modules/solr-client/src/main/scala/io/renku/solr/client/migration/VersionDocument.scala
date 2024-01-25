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

package io.renku.solr.client.migration

import io.bullet.borer.{Decoder, Encoder}
import io.bullet.borer.derivation.MapBasedCodecs.{deriveDecoder, deriveEncoder}

final private[client] case class VersionDocument(id: String, currentSchemaVersion: Long)

private[client] object VersionDocument:
  given Encoder[VersionDocument] = deriveEncoder
  given Decoder[VersionDocument] = deriveDecoder
