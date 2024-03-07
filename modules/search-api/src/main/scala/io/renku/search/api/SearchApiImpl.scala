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

package io.renku.search.api

import cats.effect.Async
import cats.syntax.all.*
import io.github.arainko.ducktape.*
import io.renku.search.api.data.*
import io.renku.search.model.users
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.Entity as SolrEntity
import io.renku.solr.client.QueryResponse
import org.http4s.dsl.Http4sDsl
import scribe.Scribe

private class SearchApiImpl[F[_]: Async](solrClient: SearchSolrClient[F])
    extends Http4sDsl[F]
    with SearchApi[F]:

  private given Scribe[F] = scribe.cats[F]

  override def query(query: QueryInput): F[Either[String, SearchResult]] =
    solrClient
      .queryEntity(query.query, query.page.limit + 1, query.page.offset)
      .map(toApiResult(query.page))
      .map(_.asRight[String])
      .handleErrorWith(errorResponse(query.query.render))
      .widen

  private def errorResponse(
      phrase: String
  ): Throwable => F[Either[String, SearchResult]] =
    err =>
      val message = s"Finding by '$phrase' phrase failed: ${err.getMessage}"
      Scribe[F]
        .error(message, err)
        .as(message)
        .map(_.asLeft[SearchResult])

  private def toApiResult(currentPage: PageDef)(
      solrResult: QueryResponse[SolrEntity]
  ): SearchResult =
    val hasMore = solrResult.responseBody.docs.size > currentPage.limit
    val pageInfo = PageWithTotals(currentPage, solrResult.responseBody.numFound, hasMore)
    val items = solrResult.responseBody.docs.map(toApiEntity)
    if (hasMore) SearchResult(items.init, pageInfo)
    else SearchResult(items, pageInfo)

  private lazy val toApiEntity: SolrEntity => SearchEntity =
    given Transformer[users.Id, UserId] = (id: users.Id) => UserId(id)
    _.to[SearchEntity]
