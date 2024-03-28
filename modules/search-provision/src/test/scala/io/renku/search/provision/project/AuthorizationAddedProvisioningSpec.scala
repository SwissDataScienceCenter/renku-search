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

package io.renku.search.provision.project

import cats.effect.{IO, Resource}

import io.renku.avro.codec.encoders.all.given
import io.renku.events.v1.{ProjectAuthorizationAdded, ProjectMemberRole}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.ModelGenerators
import io.renku.search.model.projects.MemberRole
import io.renku.search.model.{Id, projects}
import io.renku.search.provision.project.AuthorizationAddedProvisioningSpec.testCases
import io.renku.search.provision.{BackgroundCollector, ProvisioningSuite}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.search.solr.documents.{Project as ProjectDocument, SolrDocument}

class AuthorizationAddedProvisioningSpec extends ProvisioningSuite:

  testCases.foreach { tc =>
    test(s"can fetch events, decode them, and update docs in Solr: $tc"):

      withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
        for {
          _ <- tc.dbState.create(solrClient)

          collector <- BackgroundCollector[SolrDocument](
            loadPartialOrEntity(solrClient, tc.projectId)
          )
          _ <- collector.start

          provisioningFiber <- handlers.projectAuthAdded.compile.drain.start

          _ <- queueClient.enqueue(
            queueConfig.projectAuthorizationAdded,
            messageHeaderGen(ProjectAuthorizationAdded.SCHEMA$).generateOne,
            tc.authAdded
          )
          _ <- collector.waitUntil(docs =>
            scribe.info(s"Check for ${tc.expectedProject}")
            docs.contains(tc.expectedProject)
          )

          _ <- provisioningFiber.cancel
        } yield ()
      }
  }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)

object AuthorizationAddedProvisioningSpec:
  enum DbState:
    case Empty
    case Project(project: ProjectDocument)
    case PartialProject(project: PartialEntityDocument.Project)

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty             => IO.unit
      case DbState.Project(p)        => solrClient.insert(Seq(p))
      case DbState.PartialProject(p) => solrClient.insert(Seq(p))

  case class TestCase(name: String, dbState: DbState, user: Id, role: MemberRole):
    val projectId = dbState match
      case DbState.Empty             => ModelGenerators.idGen.generateOne
      case DbState.Project(p)        => p.id
      case DbState.PartialProject(p) => p.id

    val authAdded: ProjectAuthorizationAdded =
      ProjectAuthorizationAdded(
        projectId.value,
        user.value,
        ProjectMemberRole.valueOf(role.name.toUpperCase())
      )

    val expectedProject: SolrDocument = dbState match
      case DbState.Empty =>
        PartialEntityDocument.Project(
          projectId,
          Set(user).filter(_ => role == MemberRole.Owner),
          Set(user).filter(_ => role == MemberRole.Member)
        )
      case DbState.Project(p) =>
        p.addMember(user, role)

      case DbState.PartialProject(p) =>
        p.add(user, role)

    override def toString = s"$name: ${user.value.take(6)}… db=$dbState"

  val testCases =
    for {
      role <- MemberRole.values.toList
      proj = SolrDocumentGenerators.projectDocumentGen.generateOne
      pproj = SolrDocumentGenerators.partialProjectGen.generateOne
      dbState <- List(DbState.Empty, DbState.Project(proj), DbState.PartialProject(pproj))
      userId = ModelGenerators.idGen.generateOne
    } yield TestCase(s"add $role", dbState, userId, role)
