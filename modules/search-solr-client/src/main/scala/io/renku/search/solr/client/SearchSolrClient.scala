package io.renku.search.solr.client

import cats.data.NonEmptyList
import cats.effect.{Async, Resource}
import fs2.Stream
import fs2.io.net.Network

import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.model.Id
import io.renku.search.query.Query
import io.renku.search.solr.SearchRole
import io.renku.search.solr.documents.*
import io.renku.solr.client.*

trait SearchSolrClient[F[_]]:
  def underlying: SolrClient[F]
  def findById[D: Decoder](id: CompoundId): F[Option[D]]
  def upsert[D: Encoder](documents: Seq[D]): F[UpsertResponse]
  def upsertSuccess[D: Encoder](documents: Seq[D]): F[Unit]
  def deleteIds(ids: NonEmptyList[Id]): F[Unit]
  def query[D: Decoder](query: QueryData): F[QueryResponse[D]]
  def queryEntity(
      role: SearchRole,
      query: Query,
      limit: Int,
      offset: Int
  ): F[QueryResponse[EntityDocument]]
  def queryAll[D: Decoder](query: QueryData): Stream[F, D]
  def deletePublicData: F[Unit]

object SearchSolrClient:
  def make[F[_]: Async: Network](
      solrConfig: SolrConfig
  ): Resource[F, SearchSolrClient[F]] =
    SolrClient[F](solrConfig).map(new SearchSolrClientImpl[F](_))
