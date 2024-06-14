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
import io.renku.search.model.*
import io.renku.search.query.Query
import io.renku.search.solr.SearchRole
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.*
import io.renku.search.solr.documents.EntityOps.*
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.DocVersion
import io.renku.solr.client.QueryData
import munit.CatsEffectSuite
import org.scalacheck.Gen

class SearchSolrClientSpec extends CatsEffectSuite with SearchSolrSuite:
  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, searchSolrClient)

  test("be able to insert and fetch a Project document"):
    val project =
      projectDocumentGen(
        "solr-project",
        "solr project description",
        Gen.const(None)
      ).generateOne
    for {
      client <- IO(searchSolrClient())
      _ <- client.upsert(Seq(project.widen))
      qr <- client.queryEntity(
        SearchRole.admin(Id("admin")),
        Query.parse("solr").toOption.get,
        10,
        0
      )
      _ = assert(
        qr.responseBody.docs.map(
          _.noneScore
            .setCreatedBy(None)
            .assertVersionNot(DocVersion.NotExists)
            .setVersion(DocVersion.NotExists)
        ) contains project
      )
      gr <- client.findById[EntityDocument](CompoundId.projectEntity(project.id))
      _ = assert(
        gr.map(
          _.assertVersionNot(DocVersion.NotExists).setVersion(DocVersion.NotExists)
        ) contains project
      )
    } yield ()

  test("be able to insert and fetch a User document"):
    val firstName = FirstName("Johnny")
    val user = userDocumentGen.generateOne.copy(firstName = firstName.some)
    for {
      client <- IO(searchSolrClient())
      _ <- client.upsert(Seq(user.widen))
      qr <- client.queryEntity(
        SearchRole.admin(Id("admin")),
        Query.parse(firstName.value).toOption.get,
        10,
        0
      )
      _ = assert(
        qr.responseBody.docs.map(
          _.noneScore
            .assertVersionNot(DocVersion.NotExists)
            .setVersion(DocVersion.NotExists)
        ) contains user
      )
      gr <- client.findById[EntityDocument](CompoundId.userEntity(user.id))
      _ = assert(
        gr.map(
          _.assertVersionNot(DocVersion.NotExists).setVersion(DocVersion.NotExists)
        ) contains user
      )
    } yield ()

  test("be able to find by the given query"):
    val firstName = FirstName("Ian")
    val user = userDocumentGen.generateOne.copy(firstName = firstName.some)
    case class UserId(id: String)
    given Decoder[UserId] = deriveDecoder[UserId]
    for {
      client <- IO(searchSolrClient())
      _ <- client.upsert(Seq(user.widen))
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

  test("query entities with different roles"):
    for
      client <- IO(searchSolrClient())
      entityMembers <- IO(entityMembersGen.suchThat(_.nonEmpty).generateOne)
      project <- IO(
        projectDocumentGenForInsert
          .map(p => p.setMembers(entityMembers).copy(visibility = Visibility.Private))
          .generateOne
      )
      _ <- client.upsertSuccess(Seq(project))
      member = entityMembers.allIds.head
      nonMember <- IO(ModelGenerators.idGen.generateOne)
      query = Query(Query.Segment.idIs(project.id.value))
      anonResult <- client.queryEntity(SearchRole.anonymous, query, 1, 0)
      nonMemberResult <- client.queryEntity(SearchRole.user(nonMember), query, 1, 0)
      memberResult <- client.queryEntity(SearchRole.user(member), query, 1, 0)
      adminResult <- client.queryEntity(SearchRole.admin(Id("admin")), query, 1, 0)

      _ = assert(anonResult.responseBody.docs.isEmpty)
      _ = assert(nonMemberResult.responseBody.docs.isEmpty)
      _ = assertEquals(memberResult.responseBody.docs.size, 1)
      _ = assertEquals(adminResult.responseBody.docs.size, 1)
      _ = assertEquals(memberResult.responseBody.docs.head.id, project.id)
      _ = assertEquals(adminResult.responseBody.docs.head.id, project.id)
    yield ()
