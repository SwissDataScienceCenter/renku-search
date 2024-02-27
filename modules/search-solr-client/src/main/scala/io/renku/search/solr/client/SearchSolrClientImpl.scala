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
import io.renku.search.solr.documents.Project
import io.renku.search.solr.query.LuceneQueryInterpreter
import io.renku.search.solr.schema.EntityDocumentSchema
import io.renku.solr.client.{QueryData, QueryString, SolrClient}
import io.renku.search.query.Query
import io.renku.solr.client.QueryResponse

private class SearchSolrClientImpl[F[_]: Async](solrClient: SolrClient[F])
    extends SearchSolrClient[F]:

  private[this] val logger = scribe.cats.effect[F]
  private[this] val interpreter = LuceneQueryInterpreter.forSync[F]

  override def insertProjects(projects: Seq[Project]): F[Unit] =
    solrClient.insert(projects).void

  override def queryProjects(
      query: Query,
      limit: Int,
      offset: Int
  ): F[QueryResponse[Project]] =
    for {
      solrQuery <- interpreter.run(query)
      _ <- logger.debug(s"Query: ${query.render} ->Solr: $solrQuery")
      res <- solrClient
        .query[Project](
          QueryData(QueryString(solrQuery.query.value, limit, offset))
            .copy(sort = solrQuery.sort)
        )
    } yield res

  override def findProjects(phrase: String): F[List[Project]] =
    solrClient
      .query[Project](
        QueryData(
          QueryString(
            s"${EntityDocumentSchema.Fields.entityType}:${Project.entityType} AND (name:$phrase OR description:$phrase)"
          )
        )
      )
      .map(_.responseBody.docs.toList)
