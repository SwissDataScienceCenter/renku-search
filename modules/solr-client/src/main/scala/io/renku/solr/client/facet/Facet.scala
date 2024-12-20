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

import cats.data.NonEmptyList

import io.renku.solr.client.schema.FieldName

enum Facet:
  // https://solr.apache.org/guide/solr/latest/query-guide/json-facet-api.html#terms-facet
  case Terms(
      name: FieldName,
      field: FieldName,
      limit: Option[Int] = None,
      minCount: Option[Int] = None,
      method: Option[FacetAlgorithm] = None,
      missing: Boolean = false,
      numBuckets: Boolean = false,
      allBuckets: Boolean = false
  )

  // https://solr.apache.org/guide/solr/latest/query-guide/json-facet-api.html#range-facet
  case ArbitraryRange(
      name: FieldName,
      field: FieldName,
      ranges: NonEmptyList[FacetRange]
  )
