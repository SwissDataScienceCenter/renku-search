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
import io.github.arainko.ducktape.*
import io.renku.search.api.data.*
import io.renku.search.model.users
import io.renku.search.query.Query
import io.renku.search.solr.client.SearchSolrClientGenerators.*
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.{Entity as SolrEntity, User as SolrUser}
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
        _ <- client.insert((project1 :: project2 :: Nil).map(_.widen))
        results <- searchApi
          .query(mkQuery("matching"))
          .map(_.fold(err => fail(s"Calling Search API failed with $err"), identity))
      } yield assert {
        results.items.map(scoreToNone) contains toApiEntity(project1)
      }
    }

  test("return Project and User entities"):
    withSearchSolrClient().use { client =>
      val project = projectDocumentGen("exclusive", "exclusive description").generateOne
      val user = SolrUser(project.createdBy, users.FirstName("exclusive").some)
      val searchApi = new SearchApiImpl[IO](client)
      for {
        _ <- client.insert(project :: user :: Nil)
        results <- searchApi
          .query(mkQuery("exclusive"))
          .map(_.fold(err => fail(s"Calling Search API failed with $err"), identity))
      } yield assert {
        toApiEntities(project, user).diff(results.items.map(scoreToNone)).isEmpty
      }
    }

  private def scoreToNone(e: SearchEntity): SearchEntity = e match
    case e: Project => e.copy(score = None)
    case e: User    => e.copy(score = None)

  private def mkQuery(phrase: String): QueryInput =
    QueryInput.pageOne(Query.parse(s"Fields $phrase").fold(sys.error, identity))

  private def toApiEntities(e: SolrEntity*) = e.map(toApiEntity)

  private def toApiEntity(e: SolrEntity) =
    given Transformer[users.Id, UserId] = (id: users.Id) => UserId(id)
    e.to[SearchEntity]
