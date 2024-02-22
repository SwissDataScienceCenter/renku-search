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

package io.renku.search.api

import sttp.tapir.query as queryParam
import io.renku.search.api.data.*
import io.renku.search.query.Query

object Params extends TapirCodecs {

  val query =
    queryParam[Query]("q").description("User defined search query")

  val pageDef = {
    val limit =
      queryParam[Int]("limit").description("The maximum number of results to return").default(25)

    val offset =
      queryParam[Int]("offset").description("How many results to skip").default(0)

    limit.and(offset).map(PageDef.apply.tupled)(Tuple.fromProductTyped)
  }

  val queryInput =
    query.and(pageDef).map(QueryInput.apply.tupled)(Tuple.fromProductTyped)
}
