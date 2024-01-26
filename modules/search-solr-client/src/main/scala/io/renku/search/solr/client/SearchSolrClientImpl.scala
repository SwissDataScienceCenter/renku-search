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
import io.renku.search.solr.schema.EntityDocumentSchema
import io.renku.solr.client.{QueryString, SolrClient}

class SearchSolrClientImpl[F[_]: Async](solrClient: SolrClient[F])
    extends SearchSolrClient[F]:

  override def insertProject(project: Project): F[Unit] =
    solrClient.insert(Seq(project)).void

  override def findAllProjects: F[List[Project]] =
    solrClient
      .query[Project](
        QueryString(s"${EntityDocumentSchema.Fields.entityType}:${Project.entityType}")
      )
      .map(_.responseBody.docs.toList)
