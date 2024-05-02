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

package io.renku.search.provision.group

import cats.effect.*
import io.renku.search.GeneratorSyntax.*
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.documents.{Group as GroupDocument, Project as ProjectDocument}
import io.renku.search.solr.client.SolrDocumentGenerators
import org.scalacheck.Gen
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.provision.group.GroupMemberAddedSpec.DbState
import io.renku.search.model.ModelGenerators
import io.renku.events.EventsGenerators
import io.renku.search.events.GroupMemberAdded
import io.renku.search.solr.documents.CompoundId
import io.renku.search.solr.documents.EntityDocument
import io.renku.solr.client.QueryData
import io.renku.solr.client.QueryString
import io.renku.search.solr.query.SolrToken

class GroupMemberAddedSpec extends ProvisioningSuite:
  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)

  test("adding member to group and related projects"):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      val initialState = DbState.groupWithProjectsGen.generateOne
      val role = ModelGenerators.memberRoleGen.generateOne
      val newMember =
        GroupMemberAdded(initialState.group.id, ModelGenerators.idGen.generateOne, role)
      for
        _ <- initialState.setup(solrClient)
        msg = EventsGenerators.eventMessageGen(Gen.const(newMember)).generateOne
        _ <- queueClient.enqueue(queueConfig.groupMemberAdded, msg)
        _ <- handlers
          .makeUpsert[GroupMemberAdded](queueConfig.groupMemberAdded)
          .take(1)
          .compile
          .toList
        currentGroup <- solrClient
          .findById[EntityDocument](
            CompoundId.groupEntity(initialState.group.id)
          )
          .map(_.get.asInstanceOf[GroupDocument])
        _ = assert(
          currentGroup.toEntityMembers.getMemberIds(role).contains(newMember.userId),
          s"new member '${newMember.userId}' not in group $role"
        )

        currentProjects <- solrClient
          .query[EntityDocument](initialState.projectQuery)
          .map(_.responseBody.docs)
          .map(_.map(_.asInstanceOf[ProjectDocument]))
        _ <- IO.println(currentProjects)
        _ = assert(
          currentProjects.forall(
            _.toGroupMembers.getMemberIds(role).contains(newMember.userId)
          ),
          s"new member '${newMember.userId}' not in projects group $role"
        )
      yield ()
    }

object GroupMemberAddedSpec:
  enum DbState:
    case GroupWithProjects(group: GroupDocument, projects: List[ProjectDocument])

    def setup(solrClient: SearchSolrClient[IO]): IO[Unit] = this match
      case GroupWithProjects(group, projects) =>
        solrClient.upsertSuccess(Seq(group)) >> solrClient.upsertSuccess(projects)

    def projectQuery: QueryData = this match
      case GroupWithProjects(_, projects) =>
        QueryData(QueryString(projects.map(p => SolrToken.idIs(p.id)).foldOr.value))

  object DbState:
    def groupWithProjectsGen: Gen[DbState.GroupWithProjects] =
      for
        groupMembers <- SolrDocumentGenerators.entityMembersGen
        group <- SolrDocumentGenerators.groupDocumentGen
          .map(_.setMembers(groupMembers))
        projects <- Gen
          .choose(1, 6)
          .flatMap(n => Gen.listOfN(n, SolrDocumentGenerators.projectDocumentGen))
          .map(
            _.map(
              _.copy(namespace = Some(group.namespace))
                .setGroupMembers(groupMembers)
            )
          )
      yield DbState.GroupWithProjects(group, projects)
