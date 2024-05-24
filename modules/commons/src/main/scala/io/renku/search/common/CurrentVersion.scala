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

package io.renku.search.common

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs

final case class CurrentVersion(
    name: String,
    version: String,
    headCommit: String,
    describedVersion: String
)

object CurrentVersion:

  given Encoder[CurrentVersion] = MapBasedCodecs.deriveEncoder
  given Decoder[CurrentVersion] = MapBasedCodecs.deriveDecoder

  lazy val get: CurrentVersion = CurrentVersion(
    name = "renku-search",
    version = io.renku.search.BuildInfo.version,
    headCommit = io.renku.search.BuildInfo.gitHeadCommit.getOrElse(""),
    describedVersion = io.renku.search.BuildInfo.gitDescribedVersion.getOrElse("")
  )
