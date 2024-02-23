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

import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.Decoder

final case class PageDef(
    limit: Int,
    offset: Int
):
  require(limit > 0, "limit must be >0")
  require(offset >= 0, "offset must be positive")

  val page: Int =
    1 + (offset / limit)

object PageDef:
  val default: PageDef = PageDef(25, 0)

  def fromPage(pageNum: Int, perPage: Int): PageDef =
    PageDef(perPage, (pageNum - 1).abs * perPage)

  given Encoder[PageDef] = MapBasedCodecs.deriveEncoder
  given Decoder[PageDef] = MapBasedCodecs.deriveDecoder
