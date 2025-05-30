package io.renku.search.api

import cats.effect.IO
import cats.syntax.all.*

import io.renku.search.GeneratorSyntax.*
import io.renku.search.api.data.*
import io.renku.search.model.*
import io.renku.search.query.Query
import io.renku.search.solr.client.SearchSolrSuite
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.EntityDocument
import io.renku.solr.client.DocVersion
import io.renku.solr.client.ResponseBody
import munit.CatsEffectSuite
import scribe.Scribe

class SearchApiSpec extends CatsEffectSuite with SearchSolrSuite:
  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, searchSolrClient)

  private given Scribe[IO] = scribe.cats[IO]

  test("do a lookup in Solr to find entities matching the given phrase"):
    val user = userDocumentGen.generateOne
    val project1 = projectDocumentGenForInsert
      .map(p =>
        p.copy(
          name = Name("matching"),
          description = Description("matching description").some,
          createdBy = user.id,
          namespace = user.namespace,
          visibility = Visibility.Public
        )
      )
      .generateOne
    val project2 = projectDocumentGenForInsert
      .map(p =>
        p.copy(
          name = Name("disparate"),
          description = Description("disparate description").some,
          createdBy = user.id,
          namespace = user.namespace,
          visibility = Visibility.Public
        )
      )
      .generateOne
    for {
      client <- IO(searchSolrClient())
      searchApi = new SearchApiImpl[IO](client)
      _ <- client.upsert((project1 :: project2 :: user :: Nil).map(_.widen))
      results <- searchApi
        .query(AuthContext.anonymous)(mkQuery("matching type:Project"))
        .map(_.fold(err => fail(s"Calling Search API failed with $err"), identity))

      expected = toApiEntities(
        project1.copy(
          creatorDetails = ResponseBody.single(user).some,
          namespaceDetails = ResponseBody.single(user).some
        )
      ).toSet
      obtained = results.items.map(scoreToNone).toSet
    } yield assert(
      expected.diff(obtained).isEmpty,
      s"Expected $expected, bot got $obtained"
    )

  test("return Project and User entities"):
    val userId = ModelGenerators.idGen.generateOne
    val user = userDocumentGen
      .map(u => u.copy(id = userId, firstName = FirstName("exclusive").some))
      .generateOne
    val project = projectDocumentGenForInsert
      .map(p =>
        p.copy(
          name = Name("exclusive"),
          description = Description("exclusive description").some,
          createdBy = userId,
          namespace = user.namespace,
          visibility = Visibility.Public
        )
      )
      .generateOne
      .copy(createdBy = userId)
    for {
      client <- IO(searchSolrClient())
      searchApi = new SearchApiImpl[IO](client)
      _ <- client.upsert[EntityDocument](project :: user :: Nil)
      results <- searchApi
        .query(AuthContext.anonymous)(mkQuery("exclusive"))
        .map(_.fold(err => fail(s"Calling Search API failed with $err"), identity))

      expected = toApiEntities(
        project.copy(
          creatorDetails = ResponseBody.single(user).some,
          namespaceDetails = ResponseBody.single(user).some
        ),
        user
      ).toSet
      obtained = results.items.map(scoreToNone).toSet
    } yield assert(
      expected.diff(obtained).isEmpty,
      s"Expected $expected, bot got $obtained"
    )

  private def scoreToNone(e: SearchEntity): SearchEntity = e match
    case e: SearchEntity.Project => e.copy(score = None)
    case e: SearchEntity.User    => e.copy(score = None)
    case e: SearchEntity.Group   => e.copy(score = None)

  private def mkQuery(phrase: String): QueryInput =
    QueryInput.pageOne(Query.parse(phrase).fold(sys.error, identity))

  private def toApiEntities(e: EntityDocument*) = e.map(EntityConverter.apply)
