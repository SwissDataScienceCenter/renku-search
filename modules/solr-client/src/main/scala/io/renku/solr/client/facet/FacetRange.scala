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
import io.bullet.borer.Writer
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key

final case class FacetRange(
    from: FacetRange.Value,
    to: FacetRange.Value,
    @key("inclusive_from") inclusiveFrom: Boolean = true,
    @key("inclusive_to") inclusiveTo: Boolean = false
)
object FacetRange:
  case object All
  type Value = Int | All.type

  given Encoder[Value] = new Encoder[Value]:
    override def write(w: Writer, v: Value) = v match
      case n: Int => w.write(n)
      case All    => w.write("*")

  given Encoder[FacetRange] = MapBasedCodecs.deriveEncoder
