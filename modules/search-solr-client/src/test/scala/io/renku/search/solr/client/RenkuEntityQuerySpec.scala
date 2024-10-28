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

package io.renku.search.solr.client

import io.renku.search.model.Id
import io.renku.search.solr.SearchRole
import io.renku.search.solr.documents.DocumentKind
import io.renku.search.solr.query.SolrQuery
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.QueryData
import io.renku.solr.client.SolrSort
import munit.FunSuite

class RenkuEntityQuerySpec extends FunSuite:
  val adminRole: SearchRole = SearchRole.admin(Id("admin"))

  def query(q: String, role: SearchRole) =
    RenkuEntityQuery(
      role,
      SolrQuery(SolrToken.unsafeFromString(q), SolrSort.empty),
      10,
      0
    )

  def assertFilter(q: QueryData, fq: String, fqn: String*) =
    (fq +: fqn).foreach { f =>
      assert(q.filter.exists(_ == f), s"Expected filter query not found: $f  [$q]")
    }

  def assertFilterNot(q: QueryData, fq: String, fqn: String*) =
    (fq +: fqn).foreach { f =>
      assert(q.filter.forall(_ != f), s"Filter query found: $f")
    }

  test("amend query with auth data"):
    assertFilter(
      query("help", SearchRole.user(Id("13"))),
      SolrToken.forUser(Id("13")).value
    )
    assertFilter(
      query("help", SearchRole.Anonymous),
      SolrToken.publicOnly.value
    )
    assertFilterNot(
      query("help", adminRole),
      SolrToken.publicOnly.value
    )

  test("only full entities"):
    assertFilter(
      query("bla", adminRole),
      SolrToken.kindIs(DocumentKind.FullEntity).value
    )
