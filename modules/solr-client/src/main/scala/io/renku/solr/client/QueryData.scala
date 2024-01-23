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

package io.renku.solr.client

import io.circe._
import io.circe.generic.semiauto._

final case class QueryData(
    query: String,
    filter: List[String],
    limit: Int,
    offset: Int,
    fields: List[Field],
    params: Map[String, String]
) {

  def nextPage: QueryData =
    copy(offset = offset + limit)

  def withHighLight(fields: List[Field], pre: String, post: String): QueryData =
    copy(params =
      params ++ Map(
        "hl" -> "on",
        "hl.requireFieldMatch" -> "true",
        "hl.fl" -> fields.map(_.name).mkString(","),
        "hl.simple.pre" -> pre,
        "hl.simple.post" -> post
      )
    )
}

object QueryData {

  given Encoder[QueryData] = deriveEncoder[QueryData]

  def selectAll: QueryData =
    QueryData(
      sanitize("*:*"),
      Nil,
      0,
      50,
      List(Field("*")),
      Map.empty // Map("defType" -> cfg.defType, "q.op" -> cfg.qOp)
    )

  private def sanitize(q: String): String =
    q.replaceAll("[\\(,\\)]+", " ")
}
