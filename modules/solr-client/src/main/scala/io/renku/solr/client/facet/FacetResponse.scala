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

package io.renku.solr.client.facet

import io.bullet.borer.Decoder
import io.bullet.borer.Reader
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.renku.solr.client.facet.FacetResponse.Values
import io.renku.solr.client.schema.FieldName

final case class FacetResponse(
    count: Int,
    buckets: Map[FieldName, Values]
):
  def isEmpty: Boolean = count == 0 && buckets.isEmpty

  private def withBuckets(field: FieldName, values: Values): FacetResponse =
    copy(buckets = buckets.updated(field, values))

object FacetResponse:
  val empty: FacetResponse = FacetResponse(0, Map.empty)

  final case class Bucket(@key("val") value: String, count: Int)
  object Bucket:
    given Decoder[Bucket] = MapBasedCodecs.deriveDecoder

  final case class Values(buckets: Buckets)
  object Values:
    given Decoder[Values] = MapBasedCodecs.deriveDecoder

  type Buckets = Seq[Bucket]

  given Decoder[FacetResponse] = new Decoder[FacetResponse] {
    def read(r: Reader): FacetResponse =
      r.readMapStart()
      r.readUntilBreak(empty) { fr =>
        val nextKey = r.readString()
        if (nextKey == "count") fr.copy(count = r.readInt())
        else fr.withBuckets(FieldName(nextKey), r.read[Values]())
      }
  }
