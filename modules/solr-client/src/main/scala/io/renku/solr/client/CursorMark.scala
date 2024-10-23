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

import io.bullet.borer.{Decoder, Encoder}

/** Allow paged results using a cursor as described here:
  * https://solr.apache.org/guide/solr/latest/query-guide/pagination-of-results.html#fetching-a-large-number-of-sorted-results-cursors
  */
enum CursorMark:
  case Start
  case Mark(value: String)

  def render: String = this match
    case Start   => "*"
    case Mark(v) => v

object CursorMark:

  given Encoder[CursorMark] =
    Encoder.forString.contramap(_.render)

  given Decoder[CursorMark] =
    Decoder.forString.map(s => if ("*" == s) CursorMark.Start else CursorMark.Mark(s))
