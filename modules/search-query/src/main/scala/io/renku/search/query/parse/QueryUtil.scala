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

package io.renku.search.query.parse

import cats.syntax.all.*
import io.renku.search.query.Query
import io.renku.search.query.Query.Segment

private[query] object QueryUtil {

  def collapse(q: Query): Query =
    Query(collapseTextSegments(q.segments))

  private def collapseTextSegments(segs: List[Segment]): List[Segment] = {
    @annotation.tailrec
    def loop(
        in: List[Segment],
        curr: Option[Segment.Text],
        result: List[Segment]
    ): List[Segment] =
      in match
        case first :: rest =>
          (first, curr) match
            case (t1: Segment.Text, tc) =>
              loop(rest, tc |+| Some(t1), result)

            case (f: Segment.Field, Some(tc)) =>
              loop(rest, None, f :: tc :: result)

            case (f: Segment.Field, None) =>
              loop(rest, None, f :: result)

        case Nil => (curr.toList ::: result).reverse

    loop(segs, None, Nil)
  }
}
