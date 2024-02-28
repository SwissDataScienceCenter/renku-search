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

import io.bullet.borer.{Decoder, Encoder}
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs
import sttp.tapir.Schema

final case class PageWithTotals(
    page: PageDef,
    totalResult: Long,
    totalPages: Int,
    prevPage: Option[Int] = None,
    nextPage: Option[Int] = None
)

object PageWithTotals:
  given Encoder[PageWithTotals] = MapBasedCodecs.deriveEncoder
  given Decoder[PageWithTotals] = MapBasedCodecs.deriveDecoder
  given Schema[PageWithTotals] = Schema.derived

  def apply(page: PageDef, totalResults: Long, hasMore: Boolean): PageWithTotals =
    PageWithTotals(
      page,
      totalResults,
      math.ceil(totalResults.toDouble / page.limit).toInt,
      Option(page.page - 1).filter(_ > 0),
      Option(page.page + 1).filter(_ => hasMore)
    )
