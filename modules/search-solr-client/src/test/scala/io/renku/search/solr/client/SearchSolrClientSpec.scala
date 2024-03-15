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
import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.users
import io.renku.search.query.Query
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.EntityOps.*
import io.renku.search.solr.documents.{EntityDocument, Project, User}
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.QueryData
import munit.CatsEffectSuite

class SearchSolrClientSpec extends CatsEffectSuite with SearchSolrSpec:

  test("be able to insert and fetch a Project document"):
    withSearchSolrClient().use { client =>
      val project =
        projectDocumentGen("solr-project", "solr project description").generateOne
      for {
        _ <- client.insert(Seq(project.widen))
        qr <- client.queryEntity(Query.parse("solr").toOption.get, 10, 0)
        _ = assert(qr.responseBody.docs.map(_.noneScore) contains project)
        gr <- client.findById[Project](project.id)
        _ = assert(gr contains project)
      } yield ()
    }

  test("be able to insert and fetch a User document"):
    withSearchSolrClient().use { client =>
      val firstName = users.FirstName("Johnny")
      val user = userDocumentGen.generateOne.copy(firstName = firstName.some)
      for {
        _ <- client.insert(Seq(user.widen))
        qr <- client.queryEntity(Query.parse(firstName.value).toOption.get, 10, 0)
        _ = assert(qr.responseBody.docs.map(_.noneScore) contains user)
        gr <- client.findById[User](user.id)
        _ = assert(gr contains user)
      } yield ()
    }

  test("be able to find by the given query"):
    withSearchSolrClient().use { client =>
      val firstName = users.FirstName("Ian")
      val user = userDocumentGen.generateOne.copy(firstName = firstName.some)
      case class UserId(id: String)
      given Decoder[UserId] = deriveDecoder[UserId]
      for {
        _ <- client.insert(Seq(user.widen))
        gr <- client.query[UserId](
          QueryData(
            s"firstName:$firstName",
            filter = Seq.empty,
            limit = 100,
            offset = 0
          ).withFields(Fields.id)
        )
        _ = assert(gr.responseBody.docs.map(_.id) contains user.id.value)
      } yield ()
    }
