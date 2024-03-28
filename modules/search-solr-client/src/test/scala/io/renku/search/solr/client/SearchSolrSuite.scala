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

import cats.effect.{IO, Resource}
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.migration.SchemaMigrator
import io.renku.solr.client.util.SolrClientBaseSuite
import io.renku.solr.client.SolrClient

abstract class SearchSolrSuite extends SolrClientBaseSuite:

  abstract class SolrFixture
      extends Fixture[Resource[IO, SearchSolrClient[IO]]]("search-solr")

  val withSearchSolrClient: SolrFixture = new SolrFixture:

    def apply(): Resource[IO, SearchSolrClient[IO]] =
      SolrClient[IO](solrConfig.copy(core = server.searchCoreName))
        .evalTap(SchemaMigrator[IO](_).migrate(Migrations.all).attempt.void)
        .map(new SearchSolrClientImpl[IO](_))

    override def beforeAll(): Unit =
      server.start()

    override def afterAll(): Unit =
      server.stop()

  override def munitFixtures: Seq[Fixture[?]] =
    List(withSearchSolrClient)
