package io.renku.search.solr.query

import java.time.Instant
import java.time.ZoneId

import cats.Id
import cats.effect.IO
import cats.syntax.all.*

import io.bullet.borer.{Decoder, Reader}
import io.renku.search.model
import io.renku.search.model.EntityType
import io.renku.search.query.Query
import io.renku.search.query.QueryGenerators
import io.renku.search.solr.SearchRole
import io.renku.search.solr.client.SearchSolrSuite
import io.renku.search.solr.documents.DocumentKind
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.{QueryData, QueryString}
import munit.ScalaCheckEffectSuite
import org.scalacheck.Test.Parameters
import org.scalacheck.effect.PropF

class LuceneQueryInterpreterSpec extends SearchSolrSuite with ScalaCheckEffectSuite:
  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, solrClientWithSchema)

  override protected def scalaCheckTestParameters: Parameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(20)

  given Decoder[Unit] = new Decoder {
    def read(r: Reader) =
      r.skipElement()
      ()
  }

  val adminRole: SearchRole = SearchRole.admin(model.Id("admin"))

  def query(s: String | Query, role: SearchRole = adminRole): QueryData =
    val userQuery: Query = s match
      case str: String => Query.parse(str).fold(sys.error, identity)
      case qq: Query   => qq

    val ctx = Context.fixed[Id](Instant.EPOCH, ZoneId.of("UTC"), role)
    val q = LuceneQueryInterpreter[Id].run(ctx, userQuery)
    QueryData(QueryString(q.query.value, 10, 0)).withSort(q.sort)

  test("valid content_all query") {
    IO(solrClientWithSchema()).flatMap { client =>
      List("hello world", "bla:test")
        .map(query(_))
        .traverse_(client.query[Unit])
    }
  }

  test("generate valid solr queries") {
    PropF.forAllF(QueryGenerators.query) { q =>
      IO(solrClientWithSchema()).flatMap { client =>
        client.query(query(q)).void
      }
    }
  }

  test("sort only") {
    val doc = Map(
      Fields.id.name -> "one",
      Fields.name.name -> "John",
      Fields.entityType.name -> EntityType.User.name,
      Fields.kind.name -> DocumentKind.FullEntity.name
    )
    PropF.forAllF(QueryGenerators.sortTerm) { order =>
      val q = Query(Query.Segment.Sort(order))
      for {
        client <- IO(solrClientWithSchema())
        _ <- client.upsert(Seq(doc))
        r <- client.query[Map[String, String]](
          query(q).withFields(Fields.id, Fields.name, Fields.entityType).withLimit(2)
        )
        _ = assert(
          r.responseBody.docs.nonEmpty,
          s"Expected at least one result, but got: ${r.responseBody.docs}"
        )
      } yield ()
    }
  }

  test("auth scenarios"):
    for {
      solr <- IO(searchSolrClient())
      data <- AuthTestData.generate
      _ <- solr.upsert(data.all)
      query = data.queryAll

      publicEntities <- solr.queryEntity(SearchRole.Anonymous, query, 50, 0)
      user1Entities <- solr.queryEntity(SearchRole.User(data.user1.id), query, 50, 0)
      user2Entities <- solr.queryEntity(SearchRole.User(data.user2.id), query, 50, 0)
      user3Entities <- solr.queryEntity(SearchRole.User(data.user3.id), query, 50, 0)
      _ = assertEquals(
        publicEntities.responseBody.docs.map(_.id).toSet,
        data.publicEntityIds.toSet
      )
      _ = assertEquals(
        user1Entities.responseBody.docs.map(_.id).toSet,
        data.user1EntityIds.toSet
      )
      _ = assertEquals(
        user2Entities.responseBody.docs.map(_.id).toSet,
        data.user2EntityIds.toSet
      )
      _ = assertEquals(
        user3Entities.responseBody.docs.map(_.id).toSet,
        data.user3EntityIds.toSet
      )
    } yield ()
