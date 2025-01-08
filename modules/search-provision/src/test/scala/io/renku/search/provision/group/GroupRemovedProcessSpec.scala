package io.renku.search.provision.group

import cats.effect.IO
import cats.syntax.all.*

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.GroupRemoved
import io.renku.search.model.{EntityType, Id}
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.{
  Group as GroupDocument,
  Project as ProjectDocument,
  *
}
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.DocVersion
import io.renku.solr.client.QueryData
import io.renku.solr.client.QueryString
import org.scalacheck.Gen

class GroupRemovedProcessSpec extends ProvisioningSuite:

  test(
    "can fetch events, decode them, remove the Group from Solr, " +
      "and turn all the group's project to partial in Solr"
  ):
    val initialState = GroupRemovedProcessSpec.DbState.create.generateOne
    for
      services <- IO(testServices())
      handler = services.syncHandler(queueConfig.groupRemoved)
      queueClient = services.queueClient
      solrClient = services.searchClient

      _ <- initialState.setup(solrClient)
      init <- initialState.loadByIds(solrClient)
      _ = assertEquals(init.setVersion(DocVersion.NotExists), initialState)

      _ <- queueClient.enqueue(
        queueConfig.groupRemoved,
        EventsGenerators
          .eventMessageGen(Gen.const(GroupRemoved(initialState.group.id)))
          .generateOne
      )

      _ <- handler.create.take(1).compile.lastOrError

      projects <- initialState.loadPartialProjects(solrClient)
      _ = assertEquals(projects.size, initialState.projects.size)
      _ = assertEquals(projects.map(_.id), initialState.projects.map(_.id))
    yield ()

object GroupRemovedProcessSpec:

  final case class DbState(group: GroupDocument, projects: Set[ProjectDocument]):
    def setVersion(v: DocVersion): DbState =
      copy(group.setVersion(v), projects.map(_.setVersion(v)))

    def setup(solrClient: SearchSolrClient[IO]): IO[Unit] =
      solrClient.upsertSuccess(Seq(group)) >> solrClient.upsertSuccess(projects.toList)

    def loadByIds(solrClient: SearchSolrClient[IO]): IO[DbState] =
      for
        group <- solrClient.findById[EntityDocument](CompoundId.groupEntity(group.id))
        pq = List(
          SolrToken.kindIs(DocumentKind.FullEntity),
          SolrToken.entityTypeIs(EntityType.Project),
          projects.toList.map(p => SolrToken.idIs(p.id)).foldOr
        ).foldAnd
        projs <- solrClient.query[EntityDocument](
          QueryData(QueryString(q = pq.value, limit = projects.size, offset = 0))
        )
      yield DbState(
        group.get.asInstanceOf[GroupDocument],
        projs.responseBody.docs.map(_.asInstanceOf[ProjectDocument]).toSet
      )

    def loadPartialProjects(
        solrClient: SearchSolrClient[IO]
    ): IO[Set[PartialEntityDocument.Project]] =
      val pq = List(
        SolrToken.kindIs(DocumentKind.PartialEntity),
        SolrToken.entityTypeIs(EntityType.Project),
        projects.toList.map(p => SolrToken.idIs(p.id)).foldOr
      ).foldAnd
      solrClient
        .query[PartialEntityDocument](QueryData(QueryString(pq.value)))
        .map(_.responseBody.docs.map(_.asInstanceOf[PartialEntityDocument.Project]).toSet)

  object DbState:
    def create: Gen[DbState] =
      for
        group <- SolrDocumentGenerators.groupDocumentGen
        projects <- SolrDocumentGenerators.projectDocumentGenForInsert.asListOfN()
      yield DbState(group, projects.toSet.map(_.copy(namespace = group.namespace.some)))
