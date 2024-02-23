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

package io.renku.search.api.tapir

import sttp.tapir.{query as queryParam, * }
import io.renku.search.api.data.*
import io.renku.search.query.Query
import io.renku.search.http.borer.TapirBorerJson

object Params extends TapirCodecs with TapirBorerJson {

  val query: EndpointInput[Query] =
    queryParam[Query]("q").description("User defined search query")

  val pageDef: EndpointInput[PageDef] = {
    val page =
      queryParam[Int]("page")
        .validate(Validator.min(1))
        .description("The page to retrieve, starting at 1")
        .default(1)

    val perPage =
      queryParam[Int]("per_page")
        .description("How many items to return for one page")
        .validate(Validator.min(1))
        .default(PageDef.default.limit)

    (page / perPage).map(PageDef.fromPage.tupled)(Tuple.fromProductTyped)
  }

  val queryInput: EndpointInput[QueryInput] = query.and(pageDef).mapTo[QueryInput]

  val pagingInfo: EndpointOutput[PageWithTotals] = {
    val perPage: EndpointOutput[Int] = header[Int]("x-per-page")
    val page: EndpointOutput[Int] = header[Int]("x-page")
    val pageDef = page.and(perPage).map(PageDef.fromPage.tupled)(pd => (pd.page, pd.limit))

    val prevPage: EndpointOutput[Option[Int]]  = header[Option[Int]]("x-prev-page")
    val nextPage: EndpointOutput[Option[Int]] = header[Option[Int]]("x-next-page")
    val total: EndpointOutput[Long] = header[Long]("x-total")
    val totalPages: EndpointOutput[Int] = header[Int]("x-total-pages")

    pageDef.and(prevPage).and(nextPage).and(total).and(totalPages).mapTo[PageWithTotals]
  }

  val searchItems: EndpointOutput[Seq[SearchEntity]] =
    borerJsonBody[Seq[SearchEntity]]

  val searchResult: EndpointOutput[SearchResult] =
    searchItems.and(pagingInfo).mapTo[SearchResult]
}
