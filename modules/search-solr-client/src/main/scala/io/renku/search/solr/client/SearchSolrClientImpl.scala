package io.renku.search.solr.client

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream

import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.model.Id
import io.renku.search.query.Query
import io.renku.search.solr.SearchRole
import io.renku.search.solr.documents.*
import io.renku.search.solr.query.LuceneQueryInterpreter
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.*

private class SearchSolrClientImpl[F[_]: Async](solrClient: SolrClient[F])
    extends SearchSolrClient[F]:

  private val logger = scribe.cats.effect[F]
  private val interpreter = LuceneQueryInterpreter.forSync[F]

  val underlying: SolrClient[F] = solrClient

  override def upsert[D: Encoder](documents: Seq[D]): F[UpsertResponse] =
    solrClient.upsert(documents)

  override def upsertSuccess[D: Encoder](documents: Seq[D]): F[Unit] =
    solrClient.upsertSuccess(documents)

  override def deleteIds(ids: NonEmptyList[Id]): F[Unit] =
    solrClient.deleteIds(ids.map(_.value))

  override def queryEntity(
      role: SearchRole,
      query: Query,
      limit: Int,
      offset: Int
  ): F[QueryResponse[EntityDocument]] =
    for {
      solrQuery <- interpreter(role).run(query)
      queryData = RenkuEntityQuery(role, solrQuery, limit, offset)
      _ <- logger.info(s"Query: '${query.render}' -> Solr: '$solrQuery'")
      res <- solrClient.query[EntityDocument](queryData)
    } yield res

  override def query[D: Decoder](query: QueryData): F[QueryResponse[D]] =
    solrClient.query[D](query)

  override def queryAll[D: Decoder](query: QueryData): Stream[F, D] =
    Stream
      .iterate(query)(_.nextPage)
      .evalMap(this.query)
      .takeWhile(_.responseBody.docs.nonEmpty)
      .flatMap(r => Stream.emits(r.responseBody.docs))

  override def findById[D: Decoder](id: CompoundId): F[Option[D]] =
    solrClient
      .query(id.toQueryData)
      .map(_.responseBody.docs.headOption)

  override def deletePublicData: F[Unit] =
    solrClient.delete(QueryString(SolrToken.kindExists.value))
