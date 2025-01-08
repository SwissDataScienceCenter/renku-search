package io.renku.search.api

import cats.effect.{Async, Resource}
import fs2.io.net.Network

import io.renku.search.api.data.*
import io.renku.search.solr.client.SearchSolrClient
import io.renku.solr.client.SolrConfig

trait SearchApi[F[_]]:
  def query(auth: AuthContext)(query: QueryInput): F[Either[String, SearchResult]]

object SearchApi:
  def apply[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, SearchApi[F]] =
    SearchSolrClient.make[F](solrConfig).map(new SearchApiImpl[F](_))
