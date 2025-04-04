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
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.DocVersion
import io.renku.solr.client.QueryData
import io.renku.solr.client.ResponseBody
import io.renku.solr.client.migration.SchemaMigrator
import munit.CatsEffectSuite
import org.scalacheck.Gen

class SearchSolrClientSpec extends CatsEffectSuite with SearchSolrSuite:
  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, searchSolrClient)

  test("ignore entities with non-resolvable namespace"):
    val user = userDocumentGen.generateOne
    val group = groupDocumentGen.generateOne
    val randomNs = ModelGenerators.namespaceGen.generateOne
    val project0 = projectDocumentGenForInsert.generateOne.copy(
      createdBy = user.id,
      namespace = group.namespace.some
    )
    val project1 = projectDocumentGenForInsert.generateOne.copy(
      createdBy = user.id,
      namespace = randomNs.some
    )

    for
      client <- IO(searchSolrClient())
      _ <- client.upsert(Seq(project0.widen, project1.widen, user.widen, group.widen))

      qr <- client.queryEntity(
        SearchRole.admin(Id("admin")),
        Query.empty,
        10,
        0
      )
      _ = assert(
        !qr.responseBody.docs.map(_.id).contains(project1.id),
        "project with non-existing namespace was in the result set"
      )
      _ = assertEquals(qr.responseBody.docs.size, 3)
    yield ()

  test("ignore entities with non-existing namespace"):
    val user = userDocumentGen.generateOne
    val group = groupDocumentGen.generateOne
    val project0 = projectDocumentGen(
      "project-test0uae",
      "project-test0uae description",
      Gen.const(None),
      Gen.const(None)
    ).generateOne.copy(createdBy = user.id, namespace = group.namespace.some)
    val project1 = projectDocumentGen(
      "project-test1",
      "project-test1 description",
      Gen.const(None),
      Gen.const(None)
    ).generateOne.copy(createdBy = user.id, namespace = None)

    for
      client <- IO(searchSolrClient())
      _ <- client.upsert(Seq(project0.widen, project1.widen, user.widen, group.widen))

      qr <- client.queryEntity(
        SearchRole.admin(Id("admin")),
        Query.parse("test0uae").toOption.get,
        10,
        0
      )
      _ = assertEquals(qr.responseBody.docs.size, 1)
    yield ()

  test("load project with resolved namespace and creator"):
    val user = userDocumentGen.generateOne
    val group = groupDocumentGen.generateOne
    val project = projectDocumentGen(
      "project-test1trfg",
      "project-test1trfg description",
      Gen.const(None),
      Gen.const(None)
    ).generateOne.copy(createdBy = user.id, namespace = group.namespace.some)

    for
      client <- IO(searchSolrClient())
      _ <- client.upsert(Seq(project.widen, user.widen, group.widen))

      qr <- client.queryEntity(
        SearchRole.admin(Id("admin")),
        Query.parse("test1trfg").toOption.get,
        10,
        0
      )
      _ = assertEquals(qr.responseBody.docs.size, 1)
      _ = assert(qr.responseBody.docs.head.isInstanceOf[Project])
      p = qr.responseBody.docs.head.asInstanceOf[Project]
      _ = assertEquals(
        p.creatorDetails.map(_.map(_.setVersion(user.version))),
        ResponseBody.single(user).some
      )
      _ = assertEquals(
        p.namespaceDetails.map(_.map(_.setVersion(group.version))),
        ResponseBody.single[NestedUserOrGroup](group).some
      )
    yield ()

  test("be able to insert and fetch a Project document"):
    for {
      client <- IO(searchSolrClient())
      user <- IO(userDocumentGen.generateOne)
      project <- IO(
        projectDocumentGenForInsert.generateOne
          .copy(
            createdBy = user.id,
            namespace = user.namespace,
            name = Name("solr project")
          )
      )
      _ <- client.upsert(Seq(project.widen, user.widen))
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
            .setNamespaceDetails(None)
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
      user <- IO(userDocumentGen.generateOne)
      project <- IO(
        projectDocumentGenForInsert
          .map(p => p.setMembers(entityMembers).copy(visibility = Visibility.Private))
          .generateOne
          .copy(createdBy = user.id, namespace = user.namespace)
      )
      _ <- client.upsertSuccess(Seq(project, user))
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

  test("search partial words"):
    for
      client <- IO(searchSolrClient())
      user <- IO(userDocumentGen.generateOne)
      project <- IO(
        projectDocumentGen(
          "NeuroDesk",
          "This is a Neurodesk project",
          Gen.const(None),
          Gen.const(None),
          Gen.const(Visibility.Public)
        ).generateOne.copy(createdBy = user.id, namespace = user.namespace)
      )
      _ <- client.upsertSuccess(Seq(project, user))
      result1 <- client.queryEntity(
        SearchRole.anonymous,
        Query(Query.Segment.text("neuro"), Query.Segment.idIs(project.id.value)),
        1,
        0
      )
      _ = assertEquals(result1.responseBody.docs.size, 1)
      _ = assertEquals(result1.responseBody.docs.head.id, project.id)

      result2 <- client.queryEntity(
        SearchRole.anonymous,
        Query(Query.Segment.nameIs("neuro"), Query.Segment.idIs(project.id.value)),
        1,
        0
      )
      _ = assertEquals(result2.responseBody.docs.size, 1)
      _ = assertEquals(result2.responseBody.docs.head.id, project.id)
    yield ()

  test("delete all entities"):
    val user = userDocumentGen.generateOne
    val group = groupDocumentGen.generateOne
    val project = projectDocumentGenForInsert.generateOne.copy(
      createdBy = user.id,
      namespace = group.namespace.some
    )
    val role = SearchRole.admin(Id("admin"))
    val query = Query(Query.Segment.idIs(user.id.value, group.id.value, project.id.value))
    for
      client <- IO(searchSolrClient())
      _ <- client.upsert(Seq(project, user, group))

      r0 <- client.queryEntity(role, query, 10, 0)
      _ = assertEquals(r0.responseBody.docs.size, 3)

      _ <- client.deletePublicData

      r1 <- client.queryEntity(role, Query.empty, 10, 0)
      _ = assertEquals(r1.responseBody.docs.size, 0)

      // make sure the internal document is still there
      v <- SchemaMigrator(client.underlying).currentVersion
      _ = assertEquals(v, Migrations.all.map(_.version).max.some)
    yield ()
