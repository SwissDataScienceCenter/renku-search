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
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.{Project as SolrProject, User as SolrUser}
import org.http4s.dsl.Http4sDsl
import scribe.Scribe
import io.renku.search.query.Query

private class SearchApiImpl[F[_]: Async](solrClient: SearchSolrClient[F])
    extends Http4sDsl[F]
    with SearchApi[F]:

  private given Scribe[F] = scribe.cats[F]

  override def find(phrase: String): F[Either[String, List[SearchEntity]]] =
    solrClient
      .findProjects(phrase)
      .map(toApiModel)
      .map(_.asRight[String])
      .handleErrorWith(errorResponse(phrase))
      .widen

  override def query(query: Query): F[Either[String, List[SearchEntity]]] =
    solrClient
      .queryProjects(query)
      .map(toApiModel)
      .map(_.asRight[String])
      .handleErrorWith(errorResponse(query.render))

  private def errorResponse(
      phrase: String
  ): Throwable => F[Either[String, List[Project]]] =
    err =>
      val message = s"Finding by '$phrase' phrase failed"
      Scribe[F]
        .error(message, err)
        .as(message)
        .map(_.asLeft[List[Project]])

  private def toApiModel(entities: List[SolrProject]): List[Project] =
    entities.map { p =>
      def toUser(user: SolrUser): User = User(user.id)
      Project(
        p.id,
        p.name,
        p.slug,
        p.repositories,
        p.visibility,
        p.description,
        toUser(p.createdBy),
        p.creationDate,
        p.members.map(toUser)
      )
    }
