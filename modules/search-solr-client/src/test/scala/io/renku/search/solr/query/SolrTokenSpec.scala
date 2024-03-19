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

import java.time.Instant

import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import munit.FunSuite

class SolrTokenSpec extends FunSuite:

  test("fold with parens"):
    assertEquals(
      List(
        SolrToken.fieldIs(SolrField.name, SolrToken.fromString("john")),
        SolrToken.fieldIs(SolrField.id, SolrToken.fromString("1"))
      ).foldAnd,
      SolrToken.unsafeFromString("(name:john AND id:1)")
    )
    assertEquals(
      List(
        SolrToken.fieldIs(SolrField.name, SolrToken.fromString("john")),
        SolrToken.fieldIs(SolrField.id, SolrToken.fromString("1"))
      ).foldOr,
      SolrToken.unsafeFromString("(name:john OR id:1)")
    )

  test("escape `:` in timestamps"):
    val i = Instant.now
    assertEquals(
      SolrToken.fromInstant(i),
      SolrToken.unsafeFromString(StringEscape.escape(i.toString, ":"))
    )
