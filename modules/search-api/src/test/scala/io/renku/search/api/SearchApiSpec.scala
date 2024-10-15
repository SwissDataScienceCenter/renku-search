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

import io.renku.search.GeneratorSyntax.*
import io.renku.search.api.data.*
import io.renku.search.model.*
import io.renku.search.query.Query
import io.renku.search.solr.client.SearchSolrSuite
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.{
  Group as SolrGroup,
  Project as SolrProject,
  User as SolrUser,
  *
}
import io.renku.solr.client.DocVersion
import io.renku.solr.client.ResponseBody
import munit.CatsEffectSuite
import org.scalacheck.Gen
import scribe.Scribe

class SearchApiSpec extends CatsEffectSuite with SearchSolrSuite:
  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, searchSolrClient)

  private given Scribe[IO] = scribe.cats[IO]

  test("do a lookup in Solr to find entities matching the given phrase"):
    val groupId = ModelGenerators.idGen.generateOne
    val group = SolrGroup.of(
      groupId,
      Name("group-1"),
      Namespace("group-1")
    )
    val project1Id = ModelGenerators.idGen.generateOne
    val project2Id = ModelGenerators.idGen.generateOne
    val user1Id = ModelGenerators.idGen.generateOne
    val user2Id = ModelGenerators.idGen.generateOne
    val user1 =
      SolrUser(user1Id, DocVersion.NotExists, namespace = Some(Namespace("user-1")))
    val user2 =
      SolrUser(user2Id, DocVersion.NotExists, namespace = Some(Namespace("user-2")))
    val project1 = SolrProject(
      project1Id,
      DocVersion.NotExists,
      name = Name("matching"),
      slug = Slug("matching"),
      description = Description("matching description").some,
      createdBy = user1.id,
      namespace = Namespace(user1.id.toString).some,
      visibility = Visibility.Public,
      creationDate = ModelGenerators.creationDateGen.generateOne
    )
    val project2 = SolrProject(
      project2Id,
      DocVersion.NotExists,
      name = Name("disparate"),
      slug = Slug("disparate"),
      description = Description("disparate description").some,
      createdBy = user2.id,
      namespace = Namespace(group.id.toString).some,
      visibility = Visibility.Public,
      creationDate = ModelGenerators.creationDateGen.generateOne
    )
    for {
      client <- IO(searchSolrClient())
      searchApi = new SearchApiImpl[IO](client)
      _ <- client.upsert(
        (project1.stripDetails :: project2.stripDetails :: user1 :: user2 :: group :: Nil)
          .map(_.widen)
      )
      results <- searchApi
        .query(AuthContext.anonymous)(mkQuery("matching"))
        .map(_.fold(err => fail(s"Calling Search API failed with $err"), identity))

      expected = toApiEntities(project1).toSet
      obtained = results.items.map(scoreToNone).toSet
    } yield assert(
      expected.diff(obtained).isEmpty,
      s"Expected $expected, bot got $obtained"
    )

  test("return Project and User entities"):
    val userId = ModelGenerators.idGen.generateOne
    val user = SolrUser(
      userId,
      DocVersion.NotExists,
      FirstName("exclusive").some,
      namespace = Namespace("user").some
    )
    val project = projectDocumentGen(
      "exclusive",
      "exclusive description",
      Gen.const(Some(user)),
      Gen.const(Some(user)),
      Gen.const(Visibility.Public)
    ).generateOne.copy(createdBy = userId)
    for {
      client <- IO(searchSolrClient())
      searchApi = new SearchApiImpl[IO](client)
      _ <- client.upsert[EntityDocument](project.stripDetails :: user :: Nil)
      results <- searchApi
        .query(AuthContext.anonymous)(mkQuery("exclusive"))
        .map(_.fold(err => fail(s"Calling Search API failed with $err"), identity))

      expected = toApiEntities(
        project.copy(creatorDetails = ResponseBody.single(user).some),
        user
      ).toSet
      obtained = results.items.map(scoreToNone).toSet
    } yield assert(
      expected.diff(obtained).isEmpty,
      s"Expected $expected, bot got $obtained"
    )

  private def scoreToNone(e: SearchEntity): SearchEntity = e match
    case e: SearchEntity.Project         => e.copy(score = None)
    case e: SearchEntity.User            => e.copy(score = None)
    case e: SearchEntity.Group           => e.copy(score = None)
    case e: SearchEntity.CompleteUser    => e.copy(score = None)
    case e: SearchEntity.CompleteProject => e.copy(score = None)

  private def mkQuery(phrase: String): QueryInput =
    QueryInput.pageOne(Query.parse(s"Fields $phrase").fold(sys.error, identity))

  private def toApiEntities(e: EntityDocument*) = e
    .map(EntityConverter.apply)
    .mapFilter(
      _ match
        case p: SearchEntity.Project         => p.maybeCompleteProject
        case p: SearchEntity.User            => p.maybeCompleteUser
        case p: SearchEntity.Group           => Some(p)
        case p: SearchEntity.CompleteUser    => Some(p)
        case p: SearchEntity.CompleteProject => Some(p)
    )
