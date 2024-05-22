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

import io.bullet.borer.Encoder
import io.bullet.borer.Writer
import io.bullet.borer.derivation.MapBasedCodecs

final case class CreateCoreRequest(
    name: String,
    configSet: String
)

object CreateCoreRequest:

  given Encoder[CreateCoreRequest] =
    given inner: Encoder[CreateCoreRequest] =
      MapBasedCodecs.deriveEncoder[CreateCoreRequest]
    new Encoder[CreateCoreRequest] {
      def write(w: Writer, v: CreateCoreRequest): Writer =
        w.writeMapOpen(1)
        w.writeMapMember("create", v)
        w.writeMapClose()
    }
