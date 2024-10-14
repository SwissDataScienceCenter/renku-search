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

import io.renku.search.api.data.*
import io.renku.search.model.EntityType
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.EntityDocument
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.QueryResponse
import io.renku.solr.client.facet.FacetResponse
import org.http4s.dsl.Http4sDsl
import scribe.Scribe

private class SearchApiImpl[F[_]: Async](solrClient: SearchSolrClient[F])
    extends Http4sDsl[F]
    with SearchApi[F]:

  private val logger: Scribe[F] = scribe.cats[F]

  override def query(
      auth: AuthContext
  )(query: QueryInput): F[Either[String, SearchResult]] =
    logger.info(show"Running query '$query' as '$auth'") >>
      solrClient
        .queryEntity(
          auth.searchRole,
          query.query,
          query.page.limit + 1,
          query.page.offset
        )
        .map(toApiResult(query.page))
        .map(_.asRight[String])
        .handleErrorWith(errorResponse(query.query.render))
        .widen

  private def errorResponse(
      phrase: String
  ): Throwable => F[Either[String, SearchResult]] =
    err =>
      val message = s"Finding by '$phrase' phrase failed: ${err.getMessage}"
      logger
        .error(message, err)
        .as(message)
        .map(_.asLeft[SearchResult])

  private def toApiResult(currentPage: PageDef)(
      solrResult: QueryResponse[EntityDocument]
  ): SearchResult =
    val hasMore = solrResult.responseBody.docs.size > currentPage.limit
    val pageInfo = PageWithTotals(currentPage, solrResult.responseBody.numFound, hasMore)
    val facets = solrResult.facetResponse
      .flatMap(_.buckets.get(Fields.entityType))
      .map { counts =>
        val all =
          counts.buckets.flatMap { case FacetResponse.Bucket(key, count) =>
            EntityType.fromString(key).toOption.map(et => et -> count)
          }.toMap
        FacetData(all)
      }
      .getOrElse(FacetData.empty)
    val allItems = solrResult.responseBody.docs.map(EntityConverter.apply)
    // NOTE: Any entities which could not be converted to the response body properly
    // are skipped and never returned. This is because the internal logic and models of the data service
    // allows for some documents to be missing parts that are needed by the UI.
    val successItems = allItems.filter(_.isSuccess).map(_.get)
    if (hasMore) SearchResult(successItems.init, facets, pageInfo)
    else SearchResult(successItems, facets, pageInfo)
