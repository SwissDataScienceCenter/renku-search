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

package io.renku.search.api.data

import io.renku.search.model.EntityType
import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs
import sttp.tapir.Schema
import io.bullet.borer.Encoder
import io.renku.search.api.tapir.SchemaSyntax.*

final case class FacetData(
    entityType: Map[EntityType, Int]
)

object FacetData:
  val empty: FacetData = FacetData(Map.empty)

  given Decoder[FacetData] = MapBasedCodecs.deriveDecoder
  given Encoder[FacetData] = MapBasedCodecs.deriveEncoder
  given Schema[FacetData] = {
    given Schema[Map[EntityType, Int]] = Schema.schemaForMap(_.name)
    Schema
      .derived[FacetData]
      .jsonExample(
        FacetData(
          Map(
            EntityType.Project -> 15,
            EntityType.User -> 3
          )
        )
      )
  }
