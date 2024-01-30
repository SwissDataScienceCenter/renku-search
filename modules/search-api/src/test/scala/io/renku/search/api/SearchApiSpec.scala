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

import cats.effect.IO
import cats.syntax.all.*
import io.renku.api.Project as ApiProject
import io.renku.avro.codec.AvroDecoder
import io.renku.avro.codec.all.given
import io.renku.avro.codec.json.AvroJsonDecoder
import io.renku.search.http.avro.AvroEntityCodec.given
import io.renku.search.solr.client.SearchSolrClientGenerators.*
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.Project as SolrProject
import munit.CatsEffectSuite
import scribe.Scribe

class SearchApiSpec extends CatsEffectSuite with SearchSolrSpec:

  private given Scribe[IO] = scribe.cats[IO]

  test("do a lookup in Solr to find entities matching the given phrase"):
    withSearchSolrClient().use { client =>
      val project1 = projectDocumentGen("matching", "matching description").generateOne
      val project2 = projectDocumentGen("disparate", "disparate description").generateOne
      val searchApi = new SearchApiImpl[IO](client)
      for {
        _ <- (project1 :: project2 :: Nil).traverse_(client.insertProject)
        response <- searchApi.find("matching")
        results <- response.as[List[ApiProject]]
      } yield assert(results contains toApiProject(project1))
    }

  private given AvroJsonDecoder[List[ApiProject]] =
    AvroJsonDecoder.decodeList(ApiProject.SCHEMA$)

  private def toApiProject(project: SolrProject) =
    ApiProject(project.id, project.name, project.description)
