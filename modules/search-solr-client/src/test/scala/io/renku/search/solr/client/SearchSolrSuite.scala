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
