package io.renku.search.provision.group

import cats.effect.IO
import cats.syntax.all.*

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.GroupUpdated
import io.renku.search.model.Id
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.client.{SearchSolrClient, SolrDocumentGenerators}
import io.renku.search.solr.documents.{
  Group as GroupDocument,
  Project as ProjectDocument,
  *
}
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.{DocVersion, QueryData, QueryString}
import org.scalacheck.Gen

class GroupUpdatedProvisioningSpec extends ProvisioningSuite:

  GroupUpdatedProvisioningSpec.testCases.foreach { tc =>
    test(s"can fetch events, decode them, and update group doc in Solr: $tc"):
      for
        services <- IO(testServices())
        handler = services.syncHandler(queueConfig.groupUpdated)
        queueClient = services.queueClient
        solrClient = services.searchClient

        _ <- tc.dbState.create(solrClient)

        _ <- queueClient.enqueue(
          queueConfig.groupUpdated,
          EventsGenerators.eventMessageGen(Gen.const(tc.groupUpdated)).generateOne
        )

        _ <- handler.create
          .take(1)
          .compile
          .toList

        group <- loadGroupPartialOrEntity(solrClient, tc.groupId)
        _ = assertEquals(group.size, 1)
        _ = assert(tc.checkExpectedGroup(group.head))

        projects <- tc.projectQuery
          .map(q => solrClient.queryAll[EntityDocument](q).compile.toList)
          .getOrElse(IO(Nil))
        _ = assert(tc.checkExpectedProjects(group.head, projects))
      yield ()
  }

object GroupUpdatedProvisioningSpec:
  enum DbState:
    case Empty
    case Group(group: GroupDocument)
    case PartialGroup(group: PartialEntityDocument.Group)
    case GroupWithProjects(group: GroupDocument, projects: List[ProjectDocument])

    def groupId: Option[Id] = this match
      case Empty                   => None
      case Group(g)                => g.id.some
      case PartialGroup(g)         => g.id.some
      case GroupWithProjects(g, _) => g.id.some

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty           => IO.unit
      case DbState.Group(g)        => solrClient.upsertSuccess(Seq(g))
      case DbState.PartialGroup(g) => solrClient.upsertSuccess(Seq(g))
      case DbState.GroupWithProjects(g, ps) =>
        solrClient.upsertSuccess(Seq(g)) >> solrClient.upsertSuccess(ps)

  case class TestCase(dbState: DbState, groupUpdated: GroupUpdated):
    def groupId: Id = groupUpdated.id

    def projectQuery: Option[QueryData] = dbState match
      case DbState.GroupWithProjects(_, projects) =>
        QueryData(QueryString(projects.map(p => SolrToken.idIs(p.id)).foldOr.value)).some
      case _ => None

    def checkExpectedProjects(
        group: SolrDocument,
        projects: List[SolrDocument]
    ): Boolean =
      dbState match
        case DbState.Empty           => projects.isEmpty
        case DbState.Group(_)        => projects.isEmpty
        case DbState.PartialGroup(_) => projects.isEmpty
        case DbState.GroupWithProjects(_, initialProjects) =>
          initialProjects.size == projects.size &&
          projects.forall { case p: ProjectDocument =>
            p.namespace == groupUpdated.namespace.some
          }

    def checkExpectedGroup(d: SolrDocument): Boolean =
      dbState match
        case DbState.Empty =>
          d match
            case n: GroupDocument => false
            case n: PartialEntityDocument.Group =>
              n.setVersion(DocVersion.Off) == groupUpdated.toModel(DocVersion.Off)

        case DbState.Group(g) =>
          d match
            case n: GroupDocument =>
              groupUpdated.toModel(g).setVersion(DocVersion.Off) ==
                n.setVersion(DocVersion.Off)
            case _ => false

        case DbState.PartialGroup(g) =>
          d match
            case n: PartialEntityDocument.Group =>
              groupUpdated.toModel(g).setVersion(DocVersion.Off) ==
                n.setVersion(DocVersion.Off)
            case _ => false

        case DbState.GroupWithProjects(g, ps) =>
          d match
            case n: GroupDocument =>
              groupUpdated.toModel(g).setVersion(DocVersion.Off) ==
                n.setVersion(DocVersion.Off)
            case _ => false

  val testCases =
    val group1 = SolrDocumentGenerators.groupDocumentGen.generateOne
    val pgroup = SolrDocumentGenerators.partialGroupGen.generateOne
    val upd = EventsGenerators.groupUpdatedGen("group-update").generateOne
    val group2 = SolrDocumentGenerators.groupDocumentGen.generateOne
    val projects = SolrDocumentGenerators.projectDocumentGenForInsert
      .asListOfN(1, 6)
      .map(
        _.map(_.copy(namespace = Some(group2.namespace), version = DocVersion.NotExists))
      )
      .generateOne

    for
      dbState <- List(
        DbState.Empty,
        DbState.Group(group1),
        DbState.PartialGroup(pgroup),
        DbState.GroupWithProjects(group2, projects)
      )
      event = upd.withId(dbState.groupId.getOrElse(upd.id))
    yield TestCase(dbState, event)
