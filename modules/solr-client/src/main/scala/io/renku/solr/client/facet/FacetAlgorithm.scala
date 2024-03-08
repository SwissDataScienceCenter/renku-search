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

package io.renku.solr.client.facet

import io.bullet.borer.Encoder

enum FacetAlgorithm:
  case DocValues
  case UnInvertedField
  case DocValuesHash
  case Enum
  case Stream
  case Smart

  private[client] def name: String = this match
    case DocValues       => "dv"
    case UnInvertedField => "uif"
    case DocValuesHash   => "dvhash"
    case Enum            => "enum"
    case Stream          => "stream"
    case Smart           => "smart"

object FacetAlgorithm:
  given Encoder[FacetAlgorithm] = Encoder.forString.contramap(_.name)
