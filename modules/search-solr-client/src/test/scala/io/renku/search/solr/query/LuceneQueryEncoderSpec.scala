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

package io.renku.search.solr.query

import java.time.{Instant, ZoneId}

import cats.Id
import cats.data.NonEmptyList as Nel

import io.renku.search.model
import io.renku.search.model.projects.MemberRole
import io.renku.search.query.*
import io.renku.search.query.{Comparison, FieldTerm}
import io.renku.search.solr.SearchRole
import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import munit.FunSuite

class LuceneQueryEncoderSpec extends FunSuite with LuceneQueryEncoders:

  val refDate: Instant = Instant.parse("2024-02-27T15:34:55Z")
  val utc: ZoneId = ZoneId.of("UTC")

  val ctx: Context[Id] = Context.fixed(refDate, utc, SearchRole.Admin)
  val createdEncoder = SolrTokenEncoder[Id, FieldTerm.Created]

  test("use date-max for greater-than"):
    val pd = PartialDateTime.unsafeFromString("2023-05")
    val date: FieldTerm.Created =
      FieldTerm.Created(Comparison.GreaterThan, Nel.of(DateTimeRef(pd)))
    assertEquals(
      createdEncoder.encode(ctx, date),
      SolrQuery(SolrToken.createdDateGt(pd.instantMax(utc)))
    )

  test("use date-min for lower-than"):
    val pd = PartialDateTime.unsafeFromString("2023-05")
    val date: FieldTerm.Created =
      FieldTerm.Created(Comparison.LowerThan, Nel.of(DateTimeRef(pd)))
    assertEquals(
      createdEncoder.encode(ctx, date),
      SolrQuery(SolrToken.createdDateLt(pd.instantMin(utc)))
    )

  test("created comparison is"):
    val cToday: FieldTerm.Created =
      FieldTerm.Created(Comparison.Is, Nel.of(DateTimeRef(RelativeDate.Today)))
    assertEquals(
      createdEncoder.encode(ctx, cToday),
      SolrQuery(SolrToken.createdDateIs(refDate))
    )

  test("single range"):
    val pd = PartialDateTime.unsafeFromString("2023-05")
    val date: FieldTerm.Created =
      FieldTerm.Created(Comparison.Is, Nel.of(DateTimeRef(pd)))
    assertEquals(
      createdEncoder.encode(ctx, date),
      SolrQuery(
        SolrToken.fieldIs(
          SolrField.creationDate,
          SolrToken.fromDateRange(pd.instantMin(utc), pd.instantMax(utc))
        )
      )
    )

  test("multiple range"):
    val pd1 = PartialDateTime.unsafeFromString("2023-05")
    val pd2 = PartialDateTime.unsafeFromString("2023-08")
    val date: FieldTerm.Created =
      FieldTerm.Created(Comparison.Is, Nel.of(DateTimeRef(pd1), DateTimeRef(pd2)))
    assertEquals(
      createdEncoder.encode(ctx, date),
      SolrQuery(
        List(
          SolrToken.fieldIs(
            SolrField.creationDate,
            SolrToken.fromDateRange(pd1.instantMin(utc), pd1.instantMax(utc))
          ),
          SolrToken.fieldIs(
            SolrField.creationDate,
            SolrToken.fromDateRange(pd2.instantMin(utc), pd2.instantMax(utc))
          )
        ).foldOr
      )
    )
