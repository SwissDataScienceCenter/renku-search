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

import cats.effect.IO
import cats.syntax.all.*
import io.renku.search.model.users
import io.renku.search.query.Query
import io.renku.search.solr.client.SearchSolrClientGenerators.*
import io.renku.search.solr.documents.EntityOps.*
import munit.CatsEffectSuite

class SearchSolrClientSpec extends CatsEffectSuite with SearchSolrSpec:

  test("be able to insert and fetch a Project document"):
    withSearchSolrClient().use { client =>
      val project =
        projectDocumentGen("solr-project", "solr project description").generateOne
      for {
        _ <- client.insert(Seq(project))
        r <- client.queryEntity(Query.parse("solr").toOption.get, 10, 0)
        _ = assert(r.responseBody.docs.map(_.noneScore) contains project)
      } yield ()
    }

  test("be able to insert and fetch a User document"):
    withSearchSolrClient().use { client =>
      val firstName = users.FirstName("Johnny")
      val user = userDocumentGen.generateOne.copy(firstName = firstName.some)
      for {
        _ <- client.insert(Seq(user))
        r <- client.queryEntity(Query.parse(firstName.value).toOption.get, 10, 0)
        _ = assert(r.responseBody.docs.map(_.noneScore) contains user)
      } yield ()
    }
