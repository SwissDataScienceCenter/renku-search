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

import cats.effect.*

import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.SolrClient
import io.renku.solr.client.migration.SchemaMigrator
import io.renku.solr.client.util.SolrClientBaseSuite

trait SearchSolrSuite extends SolrClientBaseSuite:

  val solrClientWithSchemaR: Resource[IO, SolrClient[IO]] =
    solrClientR.evalTap(c => SchemaMigrator[IO](c).migrate(Migrations.all))

  val searchSolrR: Resource[IO, SearchSolrClient[IO]] =
    solrClientWithSchemaR.map(new SearchSolrClientImpl[IO](_))

  val solrClientWithSchema =
    ResourceSuiteLocalFixture("solr-client-with-schema", solrClientWithSchemaR)

  val searchSolrClient =
    ResourceSuiteLocalFixture("search-solr-client", searchSolrR)
