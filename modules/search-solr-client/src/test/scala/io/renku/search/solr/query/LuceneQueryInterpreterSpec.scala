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
import java.time.ZoneId

import cats.Id
import cats.effect.IO
import cats.syntax.all.*

import io.bullet.borer.{Decoder, Reader}
import io.renku.search.LoggingConfigure
import io.renku.search.model
import io.renku.search.model.EntityType
import io.renku.search.query.Query
import io.renku.search.query.QueryGenerators
import io.renku.search.solr.SearchRole
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.migration.SchemaMigrator
import io.renku.solr.client.{QueryData, QueryString}
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Test.Parameters
import org.scalacheck.effect.PropF

class LuceneQueryInterpreterSpec
    extends CatsEffectSuite
    with LoggingConfigure
    with ScalaCheckEffectSuite
    with SearchSolrSpec:

  override protected lazy val coreName: String = server.testCoreName2

  override protected def scalaCheckTestParameters: Parameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(20)

  given Decoder[Unit] = new Decoder {
    def read(r: Reader) =
      r.skipElement()
      ()
  }

  def query(s: String | Query, role: SearchRole = SearchRole.Admin): QueryData =
    val userQuery: Query = s match
      case str: String => Query.parse(str).fold(sys.error, identity)
      case qq: Query   => qq

    val ctx = Context.fixed[Id](Instant.EPOCH, ZoneId.of("UTC"))
    val q = LuceneQueryInterpreter[Id].run(ctx, role, userQuery)
    QueryData(QueryString(q.query.value, 10, 0)).withSort(q.sort)

  def withSolr =
    withSolrClient().evalTap(c => SchemaMigrator[IO](c).migrate(Migrations.all).void)

  test("amend query with auth data"):
    assertEquals(
      query("help", SearchRole.user(model.Id("13"))).query,
      "(content_all:help) AND (visibility:public OR owners:13 OR members:13)"
    )
    assertEquals(
      query("help", SearchRole.Anonymous).query,
      "(content_all:help) AND visibility:public"
    )
    assertEquals(query("help", SearchRole.Admin).query, "content_all:help")

  test("amend empty query with auth data"):
    assertEquals(
      query("", SearchRole.user(model.Id("13"))).query,
      "(_type:*) AND (visibility:public OR owners:13 OR members:13)"
    )
    assertEquals(
      query("", SearchRole.Anonymous).query,
      "(_type:*) AND visibility:public"
    )
    assertEquals(query("", SearchRole.Admin).query, "_type:*")

  test("valid content_all query"):
    withSolr.use { client =>
      List("hello world", "role:test")
        .map(query(_))
        .traverse_(client.query[Unit])
    }

  test("generade valid solr queries"):
    PropF.forAllF(QueryGenerators.query) { q =>
      withSolr
        .use { client =>
          client.query(query(q)).void
        }
    }

  test("sort only"):
    val doc = Map(
      Fields.id.name -> "one",
      Fields.name.name -> "John",
      Fields.entityType.name -> EntityType.User.name
    )
    PropF.forAllF(QueryGenerators.sortTerm) { order =>
      val q = Query(Query.Segment.Sort(order))
      withSolr.use { client =>
        for {
          _ <- client.insert(Seq(doc))
          r <- client.query[Map[String, String]](
            query(q).withFields(Fields.id, Fields.name, Fields.entityType).withLimit(2)
          )
          _ = assert(
            r.responseBody.docs.size >= 1,
            s"Expected at least one result, but got: ${r.responseBody.docs}"
          )
        } yield ()
      }
    }

  test("auth scenarios"):
    withSearchSolrClient().use { solr =>
      for {
        data <- AuthTestData.generate
        _ <- solr.insert(data.all)
        query = data.queryAll

        publicEntities <- solr.queryEntity(SearchRole.Anonymous, query, 50, 0)
        user1Entities <- solr.queryEntity(SearchRole.User(data.user1.id), query, 50, 0)
        user2Entities <- solr.queryEntity(SearchRole.User(data.user2.id), query, 50, 0)
        user3Entities <- solr.queryEntity(SearchRole.User(data.user3.id), query, 50, 0)
        _ = assertEquals(
          publicEntities.responseBody.docs.map(_.id).toSet,
          data.publicEntityIds.toSet
        )
        _ = assertEquals(
          user1Entities.responseBody.docs.map(_.id).toSet,
          data.user1EntityIds.toSet
        )
        _ = assertEquals(
          user2Entities.responseBody.docs.map(_.id).toSet,
          data.user2EntityIds.toSet
        )
        _ = assertEquals(
          user3Entities.responseBody.docs.map(_.id).toSet,
          data.user3EntityIds.toSet
        )
      } yield ()
    }
