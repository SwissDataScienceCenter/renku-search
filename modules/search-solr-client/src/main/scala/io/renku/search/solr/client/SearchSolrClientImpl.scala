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

import cats.effect.Async
import cats.syntax.all.*
import io.bullet.borer.Encoder
import io.renku.search.query.Query
import io.renku.search.solr.documents.{DocumentId, Entity}
import io.renku.search.solr.query.LuceneQueryInterpreter
import io.renku.solr.client.{QueryData, QueryResponse, QueryString, SolrClient}
import cats.data.NonEmptyList
import io.renku.solr.client.schema.FieldName
import io.renku.solr.client.facet.{Facet, Facets}
import io.renku.search.solr.schema.EntityDocumentSchema

import scala.reflect.ClassTag

private class SearchSolrClientImpl[F[_]: Async](solrClient: SolrClient[F])
    extends SearchSolrClient[F]:

  private[this] val logger = scribe.cats.effect[F]
  private[this] val interpreter = LuceneQueryInterpreter.forSync[F]

  private val typeTerms = Facet.Terms(
    EntityDocumentSchema.Fields.entityType,
    EntityDocumentSchema.Fields.entityType
  )

  override def insert[D: Encoder](documents: Seq[D]): F[Unit] =
    solrClient.insert(documents).void

  override def deleteIds(ids: NonEmptyList[DocumentId]): F[Unit] =
    solrClient.deleteIds(ids.map(_.name)).void

  override def queryEntity(
      query: Query,
      limit: Int,
      offset: Int
  ): F[QueryResponse[Entity]] =
    for {
      solrQuery <- interpreter.run(query)
      _ <- logger.debug(s"Query: ${query.render} ->Solr: $solrQuery")
      res <- solrClient
        .query[Entity](
          QueryData(QueryString(solrQuery.query.value, limit, offset))
            .withSort(solrQuery.sort)
            .withFacet(Facets(typeTerms))
            .withFields(FieldName.all, FieldName.score)
        )
    } yield res

  override def findById[D <: Entity](id: String)(using ct: ClassTag[D]): F[Option[D]] =
    solrClient.findById[Entity](id).map(_.responseBody.docs.headOption).flatMap {
      case Some(e: D) => Some(e).pure[F]
      case Some(e) =>
        new Exception(s"Entity '$id' is of type ${e.getClass} not ${ct.runtimeClass}")
          .raiseError[F, Option[D]]
      case None => Option.empty[D].pure[F]
    }
