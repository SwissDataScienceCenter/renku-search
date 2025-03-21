package io.renku.search.provision.project

import cats.effect.IO

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.ProjectMemberAdded
import io.renku.search.model.*
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.project.AuthorizationAddedProvisioningSpec.testCases
import io.renku.search.solr.client.{SearchSolrClient, SolrDocumentGenerators}
import io.renku.search.solr.documents.{
  PartialEntityDocument,
  Project as ProjectDocument,
  SolrDocument
}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class AuthorizationAddedProvisioningSpec extends ProvisioningSuite:

  testCases.foreach { tc =>
    test(s"can fetch events, decode them, and update docs in Solr: $tc"):
      for {
        services <- IO(testServices())
        handler = services.syncHandler(queueConfig.projectAuthorizationAdded)
        queueClient = services.queueClient
        solrClient = services.searchClient

        _ <- tc.dbState.create(solrClient)

        _ <- queueClient.enqueue(
          queueConfig.projectAuthorizationAdded,
          EventsGenerators.eventMessageGen(Gen.const(tc.authAdded)).generateOne
        )

        result <- handler.create.take(1).compile.lastOrError

        found <- loadProjectPartialOrEntity(solrClient, tc.projectId)

        _ = assert(found.exists(tc.checkExpected))
      } yield ()
  }

object AuthorizationAddedProvisioningSpec:
  enum DbState:
    case Empty
    case Project(project: ProjectDocument)
    case PartialProject(project: PartialEntityDocument.Project)

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty             => IO.unit
      case DbState.Project(p)        => solrClient.upsert(Seq(p))
      case DbState.PartialProject(p) => solrClient.upsert(Seq(p))

  case class TestCase(name: String, dbState: DbState, user: Id, role: MemberRole):
    val projectId = dbState match
      case DbState.Empty             => ModelGenerators.idGen.generateOne
      case DbState.Project(p)        => p.id
      case DbState.PartialProject(p) => p.id

    val authAdded: ProjectMemberAdded =
      EventsGenerators.projectMemberAdded(projectId, user, role).generateOne

    val expectedProject: SolrDocument = dbState match
      case DbState.Empty =>
        PartialEntityDocument.Project(
          id = projectId,
          version = DocVersion.Off,
          owners = Set(user).filter(_ => role == MemberRole.Owner),
          members = Set(user).filter(_ => role == MemberRole.Member),
          viewers = Set(user).filter(_ => role == MemberRole.Viewer),
          editors = Set(user).filter(_ => role == MemberRole.Editor)
        )
      case DbState.Project(p) =>
        p.modifyEntityMembers(_.addMember(user, role))

      case DbState.PartialProject(p) =>
        p.modifyEntityMembers(_.addMember(user, role))

    def checkExpected(d: SolrDocument): Boolean =
      d.setVersion(DocVersion.Off) == expectedProject.setVersion(DocVersion.Off)

    override def toString = s"$name: ${user.value.take(6)}… db=$dbState"

  val testCases =
    for {
      role <- MemberRole.valuesV1.toList
      proj = SolrDocumentGenerators.projectDocumentGenForInsert.generateOne
      pproj = SolrDocumentGenerators.partialProjectGen.generateOne
      dbState <- List(DbState.Empty, DbState.Project(proj), DbState.PartialProject(pproj))
      userId = ModelGenerators.idGen.generateOne
    } yield TestCase(s"add $role", dbState, userId, role)
