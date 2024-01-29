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
import io.renku.api.Project as ApiProject
import io.renku.avro.codec.all.given
import io.renku.avro.codec.json.AvroJsonEncoder
import io.renku.search.http.avro.AvroEntityCodec.Implicits.entityEncoder
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.Project as SolrProject
import org.http4s.Response
import org.http4s.dsl.Http4sDsl

private class SearchApiImpl[F[_]: Async](solrClient: SearchSolrClient[F])
    extends Http4sDsl[F]
    with SearchApi[F]:

  override def find(phrase: String): F[Response[F]] =
    solrClient.findProjects(phrase).map(toApiModel).map(toAvroResponse)

  private given AvroJsonEncoder[List[ApiProject]] =
    AvroJsonEncoder.encodeList[ApiProject](ApiProject.SCHEMA$)

  private def toAvroResponse(entities: List[ApiProject]): Response[F] =
    Response[F](Ok)
      .withEntity(entities)

  private def toApiModel(entities: List[SolrProject]): List[ApiProject] =
    entities.map(p => ApiProject(p.id, p.name, p.description))
