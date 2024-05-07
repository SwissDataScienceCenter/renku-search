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

import io.bullet.borer.Json
import io.renku.solr.client.schema.FieldName
import munit.FunSuite

class FacetsSpec extends FunSuite:

  test("encode multiple facets into object"):
    val terms1 = Facet.Terms(FieldName("type"), FieldName("_type"))
    val terms2 = Facet.Terms(FieldName("cat"), FieldName("category"))
    val json = Json.encode(Facets(terms1, terms2)).toUtf8String
    assertEquals(
      json,
      """{"type":{"type":"terms","field":"_type","missing":false,"numBuckets":false,"allBuckets":false},"cat":{"type":"terms","field":"category","missing":false,"numBuckets":false,"allBuckets":false}}"""
    )

  test("encode arbitrary range"):
    val range = Facet.ArbitraryRange(
      FieldName("stars"),
      FieldName("stars"),
      NonEmptyList.of(
        FacetRange(FacetRange.All, 100),
        FacetRange(100, 200),
        FacetRange(200, FacetRange.All)
      )
    )
    val json = Json.encode(Facets(range)).toUtf8String
    assertEquals(
      json,
      """{"stars":{"type":"range","field":"stars","ranges":[{"from":"*","to":100},{"from":100,"to":200},{"from":200,"to":"*"}]}}"""
    )
