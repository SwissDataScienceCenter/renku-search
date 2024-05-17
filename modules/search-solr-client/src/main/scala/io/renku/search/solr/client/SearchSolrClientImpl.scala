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
import io.renku.search.solr.schema.EntityDocumentSchema
import io.renku.solr.client.*
import io.renku.solr.client.facet.{Facet, Facets}
import io.renku.solr.client.schema.FieldName

private class SearchSolrClientImpl[F[_]: Async](solrClient: SolrClient[F])
    extends SearchSolrClient[F]:

  private val logger = scribe.cats.effect[F]
  private val interpreter = LuceneQueryInterpreter.forSync[F]

  private val typeTerms = Facet.Terms(
    EntityDocumentSchema.Fields.entityType,
    EntityDocumentSchema.Fields.entityType
  )

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
      _ <- logger.info(s"Query: '${query.render}' -> Solr: '$solrQuery'")
      res <- solrClient
        .query[EntityDocument](
          QueryData(QueryString(solrQuery.query.value, limit, offset))
            .withSort(solrQuery.sort)
            .withFacet(Facets(typeTerms))
            .withFields(FieldName.all, FieldName.score)
        )
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
