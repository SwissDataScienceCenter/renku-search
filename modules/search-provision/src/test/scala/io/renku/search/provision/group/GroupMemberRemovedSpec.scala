package io.renku.search.provision.group

import cats.effect.*

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.GroupMemberRemoved
import io.renku.search.model.{Id, MemberRole, ModelGenerators}
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.group.GroupMemberRemovedSpec.DbState
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.documents.CompoundId
import io.renku.search.solr.documents.EntityDocument
import io.renku.search.solr.documents.{Group as GroupDocument, Project as ProjectDocument}
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.QueryData
import io.renku.solr.client.QueryString
import org.scalacheck.Gen

class GroupMemberRemovedSpec extends ProvisioningSuite:
  test("adding member to group and related projects"):
    val initialState = DbState.groupWithProjectsGen.generateOne
    val role = ModelGenerators.memberRoleGen.generateOne
    val removeMember = GroupMemberRemoved(initialState.group.id, initialState.user)
    for
      services <- IO(testServices())
      handler = services.syncHandler(queueConfig.groupMemberRemoved)
      queueClient = services.queueClient
      solrClient = services.searchClient

      _ <- initialState.setup(solrClient)
      msg = EventsGenerators.eventMessageGen(Gen.const(removeMember)).generateOne
      _ <- queueClient.enqueue(queueConfig.groupMemberRemoved, msg)
      _ <- handler.create
        .take(1)
        .compile
        .lastOrError
      currentGroup <- solrClient
        .findById[EntityDocument](
          CompoundId.groupEntity(initialState.group.id)
        )
        .map(_.get.asInstanceOf[GroupDocument])
      _ = assert(
        !currentGroup.toEntityMembers.getMemberIds(role).contains(removeMember.userId),
        s"member '${removeMember.userId}' still in group $role"
      )

      currentProjects <- solrClient
        .query[EntityDocument](initialState.projectQuery)
        .map(_.responseBody.docs)
        .map(_.map(_.asInstanceOf[ProjectDocument]))
      _ = assertEquals(currentProjects.size, initialState.projects.size)
      _ = assert(
        currentProjects.forall(
          !_.toGroupMembers.getMemberIds(role).contains(removeMember.userId)
        ),
        s"member '${removeMember.userId}' still in projects group $role"
      )
    yield ()

object GroupMemberRemovedSpec:
  enum DbState:
    case GroupWithProjects(
        group: GroupDocument,
        projects: List[ProjectDocument],
        user: Id,
        role: MemberRole
    )

    def setup(solrClient: SearchSolrClient[IO]): IO[Unit] = this match
      case GroupWithProjects(group, projects, _, _) =>
        solrClient.upsertSuccess(Seq(group)) >> solrClient.upsertSuccess(projects)

    def projectQuery: QueryData = this match
      case GroupWithProjects(_, projects, _, _) =>
        QueryData(QueryString(projects.map(p => SolrToken.idIs(p.id)).foldOr.value))

  object DbState:
    def groupWithProjectsGen: Gen[DbState.GroupWithProjects] =
      for
        userId <- ModelGenerators.idGen
        role <- ModelGenerators.memberRoleGen
        groupMembers <- SolrDocumentGenerators.entityMembersGen.map(
          _.addMember(userId, role)
        )
        group <- SolrDocumentGenerators.groupDocumentGen
          .map(_.setMembers(groupMembers))
        projects <- Gen
          .choose(1, 6)
          .flatMap(n =>
            Gen.listOfN(n, SolrDocumentGenerators.projectDocumentGenForInsert)
          )
          .map(
            _.map(
              _.copy(namespace = Some(group.namespace))
                .setGroupMembers(groupMembers)
            )
          )
      yield DbState.GroupWithProjects(group, projects, userId, role)
