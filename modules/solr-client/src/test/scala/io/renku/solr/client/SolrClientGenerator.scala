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

import io.renku.search.model.CommonGenerators
import io.renku.solr.client.facet.*
import io.renku.solr.client.schema.FieldName
import org.scalacheck.Gen

object SolrClientGenerator:

  private val fieldNameString: Gen[String] =
    Gen.choose(4, 12).flatMap(n => Gen.listOfN(n, Gen.alphaLowerChar)).map(_.mkString)

  val versionGen: Gen[DocVersion] =
    Gen.chooseNum(2L, Long.MaxValue - 1).map(DocVersion.Exact.apply)

  val fieldNameTypeStr: Gen[FieldName] =
    fieldNameString.map(n => s"${n}_s").map(FieldName.apply)

  val fieldNameTypeInt: Gen[FieldName] =
    fieldNameString.map(n => s"${n}_i").map(FieldName.apply)

  val fieldNameLiteral: Gen[FieldName] =
    fieldNameString.map(FieldName.apply)

  val facetTerms: Gen[Facet.Terms] =
    for {
      name <- fieldNameLiteral
      field <- Gen.oneOf(fieldNameTypeStr, fieldNameTypeInt)
      limit <- Gen.choose(1, 10)
    } yield Facet.Terms(name, field, Some(limit))

  val facetRangeValue: Gen[FacetRange.Value] =
    Gen.oneOf(Gen.const(FacetRange.All), Gen.choose(0, 1000))

  val facetRange: Gen[FacetRange] =
    for {
      from <- facetRangeValue
      to <- from match
        case FacetRange.All => Gen.choose(0, 500)
        case n: Int => Gen.oneOf(Gen.const(FacetRange.All), Gen.choose(0, 500).map(_ + n))
    } yield FacetRange(from, to)

  val facetArbitraryRange: Gen[Facet.ArbitraryRange] =
    for {
      name <- fieldNameLiteral
      field <- fieldNameTypeInt
      numRanges <- Gen.choose(1, 5)
      ranges <- CommonGenerators.nelOfN(numRanges, facetRange)
    } yield Facet.ArbitraryRange(name, field, ranges)

  val facet: Gen[Facet] = Gen.oneOf(facetTerms, facetArbitraryRange)

  val facets: Gen[Facets] =
    Gen.choose(0, 5).flatMap(n => Gen.listOfN(n, facet)).map { n =>
      if (n.isEmpty) Facets.empty
      else Facets(n*)
    }
