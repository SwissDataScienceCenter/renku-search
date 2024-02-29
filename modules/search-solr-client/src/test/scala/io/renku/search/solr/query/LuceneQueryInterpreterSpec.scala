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

import cats.Id
import cats.effect.IO
import cats.syntax.all.*
import io.bullet.borer.{Decoder, Reader}
import io.renku.search.query.Query
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.{QueryData, QueryString}
import io.renku.solr.client.migration.SchemaMigrator
import io.renku.solr.client.util.SolrSpec
import munit.CatsEffectSuite

import java.time.Instant
import java.time.ZoneId
import io.renku.search.query.QueryGenerators
import munit.ScalaCheckSuite
import org.scalacheck.Prop

class LuceneQueryInterpreterSpec
    extends CatsEffectSuite
    with ScalaCheckSuite
    with SolrSpec:

  override protected lazy val coreName: String = server.testCoreName2

  given Decoder[Unit] = new Decoder {
    def read(r: Reader) = ()
  }

  def query(s: String | Query): QueryData =
    val userQuery: Query = s match
      case str: String => Query.parse(str).fold(sys.error, identity)
      case qq: Query   => qq

    val ctx = Context.fixed[Id](Instant.EPOCH, ZoneId.of("UTC"))
    val q = LuceneQueryInterpreter[Id].run(ctx, userQuery)
    QueryData(QueryString(q.query.value, 0, 10)).withSort(q.sort)

  def withSolr =
    withSolrClient().evalTap(c => SchemaMigrator[IO](c).migrate(Migrations.all).void)

  test("valid content_all query"):
    withSolr.use { client =>
      List("hello world", "role:test")
        .map(query)
        .traverse_(client.query[Unit])
    }

  property("generade valid solr queries"):
    Prop.forAll(QueryGenerators.query) { q =>
      withSolr
        .use { client =>
          client.query(query(q))
        }
        .unsafeRunAndForget()
    }

  test("generated queries are syntactically correct"):
    val q = QueryGenerators.query.sample.get
    withSolr.use { client =>
      client.query[Unit](query(q))
    }
