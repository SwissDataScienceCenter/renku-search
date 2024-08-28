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

package io.renku.search.provision.user

import cats.effect.IO

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.*
import io.renku.search.model.{EntityType, Id, ModelGenerators}
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.EntityDocument
import io.renku.search.solr.documents.{
  Group as GroupDocument,
  Project as ProjectDocument,
  User as UserDocument
}
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.QueryData
import io.renku.solr.client.QueryString
import org.scalacheck.Gen

class UserRemovedProcessSpec extends ProvisioningSuite:
  private val logger = scribe.cats.io

  UserRemovedProcessSpec.testCases.foreach { tc =>
    test(s"process user removed: $tc"):
      for
        services <- IO(testServices())
        handler = services.messageHandlers
        queueClient = services.queueClient
        solrClient = services.searchClient

        _ <- tc.setup(solrClient)

        msgId <- queueClient.enqueue(
          queueConfig.userRemoved,
          EventsGenerators.eventMessageGen(Gen.const(tc.userRemovedEvent)).generateOne
        )

        _ <- handler.makeUserRemoved.take(1).compile.drain

        users <- loadPartialOrEntity(solrClient, EntityType.User, tc.userId)
        _ = assert(users.isEmpty)

        projects <- solrClient
          .queryAll[EntityDocument](QueryData(QueryString(tc.projectsQuery.value)))
          .compile
          .toList
        groups <- solrClient
          .queryAll[EntityDocument](QueryData(QueryString(tc.groupsQuery.value)))
          .compile
          .toList

        _ = assertEquals(projects.size, tc.initialProjectsCount)
        _ = assertEquals(groups.size, tc.initialGroupsCount)

        _ = assert(
          projects.forall {
            case p: ProjectDocument => !p.toEntityMembers.contains(tc.userId)
            case _                  => false
          },
          "user is still in project members"
        )
        _ = assert(
          groups.forall {
            case g: GroupDocument => !g.toEntityMembers.contains(tc.userId)
            case _                => false
          },
          "user is still in group members"
        )

        last <- queueClient.findLastProcessed(queueConfig.userRemoved)
        _ = assertEquals(last, Some(msgId))
      yield ()
  }

object UserRemovedProcessSpec:

  enum DbState:
    case ProjectAndGroups(
        user: UserDocument,
        userProjects: List[ProjectDocument],
        userGroups: List[GroupDocument]
    )

  final case class TestCase(userId: Id, initialState: DbState):
    def setup(solrClient: SearchSolrClient[IO]): IO[Unit] =
      initialState match
        case DbState.ProjectAndGroups(user, proj, gr) =>
          solrClient.upsertSuccess(Seq(user)) >>
            solrClient.upsertSuccess(proj) >>
            solrClient.upsertSuccess(gr)

    val userRemovedEvent: UserRemoved = UserRemoved(userId)

    val initialProjectsCount = initialState match
      case DbState.ProjectAndGroups(_, p, _) => p.size

    val initialGroupsCount = initialState match
      case DbState.ProjectAndGroups(_, _, g) => g.size

    val projectsQuery = initialState match
      case DbState.ProjectAndGroups(_, projects, _) =>
        List(
          SolrToken.entityTypeIs(EntityType.Project),
          projects.map(_.id).map(SolrToken.idIs).foldOr
        ).foldAnd

    val groupsQuery = initialState match
      case DbState.ProjectAndGroups(_, _, groups) =>
        List(
          SolrToken.entityTypeIs(EntityType.Group),
          groups.map(_.id).map(SolrToken.idIs).foldOr
        ).foldAnd

  val testCases: List[TestCase] =
    val role = ModelGenerators.memberRoleGen.generateOne
    val user = SolrDocumentGenerators.userDocumentGen.generateOne
    val proj = SolrDocumentGenerators.projectDocumentGenForInsert
      .asListOfN(1, 3)
      .map(_.map(_.modifyEntityMembers(_.addMember(user.id, role))))
      .generateOne
    val groups = SolrDocumentGenerators.groupDocumentGen
      .asListOfN(1, 3)
      .map(_.map(_.modifyEntityMembers(_.addMember(user.id, role))))
      .generateOne
    val dbState = DbState.ProjectAndGroups(user, proj, groups)
    List(TestCase(user.id, dbState))
