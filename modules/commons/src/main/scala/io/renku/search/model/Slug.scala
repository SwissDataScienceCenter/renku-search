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

import cats.kernel.Order

import io.bullet.borer.Codec
import io.github.arainko.ducktape.*

opaque type Slug = String
object Slug:
  def apply(v: String): Slug = v
  extension (self: Slug) def value: String = self
  given Transformer[String, Slug] = apply
  given Codec[Slug] = Codec.of[String]
  given Order[Slug] = Order.fromComparable[String]
