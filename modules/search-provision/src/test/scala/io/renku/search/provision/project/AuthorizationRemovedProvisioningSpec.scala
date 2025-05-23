package io.renku.search.provision
package project

import cats.effect.IO

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.ProjectMemberRemoved
import io.renku.search.model.Id
import io.renku.search.model.ModelGenerators
import io.renku.search.provision.project.AuthorizationRemovedProvisioningSpec.testCases
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.documents.{Project as ProjectDocument, *}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class AuthorizationRemovedProvisioningSpec extends ProvisioningSuite:
  testCases.foreach { tc =>
    test(s"can fetch events, decode them, and update docs in Solr: $tc"):

      for {
        services <- IO(testServices())
        handler = services.syncHandler(queueConfig.projectAuthorizationRemoved)
        queueClient = services.queueClient
        solrClient = services.searchClient

        _ <- tc.dbState.create(solrClient)

        _ <- queueClient.enqueue(
          queueConfig.projectAuthorizationRemoved,
          EventsGenerators.eventMessageGen(Gen.const(tc.authRemoved)).generateOne
        )

        result <- handler.create.take(1).compile.lastOrError

        found <- loadProjectPartialOrEntity(solrClient, tc.projectId)

        _ = assert(tc.checkExpected(found.toSet))
      } yield ()
  }

object AuthorizationRemovedProvisioningSpec:
  enum DbState:
    case Empty
    case Project(project: ProjectDocument)
    case PartialProject(project: PartialEntityDocument.Project)

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty             => IO.unit
      case DbState.Project(p)        => solrClient.upsert(Seq(p))
      case DbState.PartialProject(p) => solrClient.upsert(Seq(p))

  case class TestCase(dbState: DbState, user: Id):
    val projectId = dbState match
      case DbState.Empty             => ModelGenerators.idGen.generateOne
      case DbState.Project(p)        => p.id
      case DbState.PartialProject(p) => p.id

    val authRemoved: ProjectMemberRemoved =
      EventsGenerators.projectMemberRemoved(projectId, user).generateOne

    val expectedProject: Set[SolrDocument] = dbState match
      case DbState.Empty =>
        Set.empty

      case DbState.Project(p) =>
        Set(p.modifyEntityMembers(_.removeMember(user)))

      case DbState.PartialProject(p) =>
        Set(p.modifyEntityMembers(_.removeMember(user)))

    def checkExpected(d: Set[SolrDocument]): Boolean =
      expectedProject
        .map(_.setVersion(DocVersion.Off))
        .diff(d.map(_.setVersion(DocVersion.Off)))
        .isEmpty

    override def toString = s"AuthRemove(${user.value.take(6)}… db=$dbState)"

  private val testCases =
    val userId = ModelGenerators.idGen.generateOne
    val userRole = ModelGenerators.memberRoleGen.generateOne
    val proj =
      SolrDocumentGenerators.projectDocumentGenForInsert.generateOne.modifyEntityMembers(
        _.addMember(userId, userRole)
      )
    val pproj =
      SolrDocumentGenerators.partialProjectGen.generateOne.modifyEntityMembers(
        _.addMember(userId, userRole)
      )
    val dbState =
      List(DbState.Empty, DbState.Project(proj), DbState.PartialProject(pproj))
    dbState.map(TestCase(_, userId))
