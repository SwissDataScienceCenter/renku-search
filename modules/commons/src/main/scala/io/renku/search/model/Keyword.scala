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

package io.renku.search.model

import cats.Eq
import io.bullet.borer.Encoder
import io.bullet.borer.Decoder

opaque type Keyword = String

object Keyword:

  def apply(kw: String): Keyword = kw

  given Eq[Keyword] = Eq.instance((a, b) => a.equalsIgnoreCase(b))
  given Encoder[Keyword] = Encoder.forString
  given Decoder[Keyword] = Decoder.forString

  extension (self: Keyword) def value: String = self
