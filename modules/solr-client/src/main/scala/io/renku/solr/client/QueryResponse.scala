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

import io.bullet.borer.Decoder
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.renku.solr.client.facet.FacetResponse

final case class QueryResponse[A](
    responseHeader: ResponseHeader,
    @key("response") responseBody: ResponseBody[A],
    @key("facets") facetResponse: Option[FacetResponse] = None,
    @key("nextCursorMark") nextCursor: Option[CursorMark] = None
):
  def map[B](f: A => B): QueryResponse[B] =
    copy(responseBody = responseBody.map(f))

object QueryResponse:
  given [A](using Decoder[A]): Decoder[QueryResponse[A]] =
    MapBasedCodecs.deriveDecoder
