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

import cats.effect.IO

import io.renku.events.EventsGenerators
import io.renku.events.EventsGenerators.projectCreatedGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.*
import io.renku.search.model.Id
import io.renku.search.model.ModelGenerators
import io.renku.search.provision.events.syntax.*
import io.renku.search.provision.handler.DocumentMerger
import io.renku.search.provision.{BackgroundCollector, ProvisioningSuite}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.documents.{
  Group as GroupDocument,
  Project as ProjectDocument,
  *
}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class ProjectCreatedProvisioningSpec extends ProvisioningSuite:
  ProjectCreatedProvisioningSpec.testCases.foreach { tc =>
    test(s"processes message and update solr: $tc"):
      for {
        services <- IO(testServices())
        handler = services.messageHandlers
        queueClient = services.queueClient
        solrClient = services.searchClient

        _ <- tc.dbState.create(solrClient)

        _ <- queueClient.enqueue(
          queueConfig.projectCreated,
          EventsGenerators
            .eventMessageGen(Gen.const(tc.projectCreated))
            .map(_.modifyHeader(_.withContentType(DataContentType.Binary)))
            .generateOne
        )

        _ <- handler
          .makeProjectUpsert[ProjectCreated](queueConfig.projectCreated)
          .take(1)
          .compile
          .toList

        doc <- loadProjectPartialOrEntity(solrClient, tc.projectId)
        _ = assertEquals(
          doc.head.setVersion(DocVersion.Off),
          tc.expectedProject.setVersion(DocVersion.Off)
        )
      } yield ()
  }

  test("can fetch events binary encoded, decode them, and send them to Solr"):
    for
      services <- IO(testServices())
      handler = services.messageHandlers
      queueClient = services.queueClient
      solrClient = services.searchClient

      provisioningFiber <- handler.projectCreated.compile.drain.start

      created = projectCreatedGen(prefix = "binary").generateOne
      _ <- queueClient.enqueue(
        queueConfig.projectCreated,
        EventsGenerators
          .eventMessageGen(Gen.const(created))
          .map(_.modifyHeader(_.withContentType(DataContentType.Binary)))
          .generateOne
      )
      collector <- BackgroundCollector(
        solrClient
          .findById[EntityDocument](CompoundId.projectEntity(created.id))
          .map(_.toSet)
      )
      _ <- collector.start
      _ <- collector.waitUntil(
        _.map(_.setVersion(DocVersion.Off)) contains created.toModel(DocVersion.Off)
      )

      _ <- provisioningFiber.cancel
    yield ()

  test("can fetch events JSON encoded, decode them, and send them to Solr"):
    for
      services <- IO(testServices())
      handler = services.messageHandlers
      queueClient = services.queueClient
      solrClient = services.searchClient

      provisioningFiber <- handler.projectCreated.compile.drain.start

      created = projectCreatedGen(prefix = "json").generateOne
      _ <- queueClient.enqueue(
        queueConfig.projectCreated,
        EventsGenerators
          .eventMessageGen(Gen.const(created))
          .map(_.modifyHeader(_.withContentType(DataContentType.Json)))
          .generateOne
      )
      collector <- BackgroundCollector(
        solrClient
          .findById[EntityDocument](CompoundId.projectEntity(created.id))
          .map(_.toSet)
      )
      _ <- collector.start
      _ <- collector.waitUntil(
        _.map(_.setVersion(DocVersion.Off)) contains created.toModel(DocVersion.Off)
      )

      _ <- provisioningFiber.cancel
    yield ()

object ProjectCreatedProvisioningSpec:
  enum DbState:
    case Empty
    case Project(project: ProjectDocument)
    case PartialProject(project: PartialEntityDocument.Project)
    case Group(group: GroupDocument)

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty             => IO.unit
      case DbState.Project(p)        => solrClient.upsertSuccess(Seq(p))
      case DbState.PartialProject(p) => solrClient.upsertSuccess(Seq(p))
      case DbState.Group(g)          => solrClient.upsertSuccess(Seq(g))

  case class TestCase(dbState: DbState):
    val projectId = dbState match
      case DbState.Empty             => ModelGenerators.idGen.generateOne
      case DbState.Project(p)        => p.id
      case DbState.PartialProject(p) => p.id
      case DbState.Group(_)          => ModelGenerators.idGen.generateOne

    val projectCreated: ProjectCreated =
      dbState match
        case DbState.Group(g) =>
          projectCreatedGen("test-").generateOne
            .withId(projectId)
            .withNamespace(g.namespace)
        case _ =>
          projectCreatedGen("test-").generateOne.withId(projectId)

    val expectedProject: SolrDocument = dbState match
      case DbState.Empty =>
        DocumentMerger[ProjectCreated].create(projectCreated).get

      case DbState.Group(g) =>
        DocumentMerger[ProjectCreated]
          .create(projectCreated)
          .get
          .asInstanceOf[ProjectDocument]
          .setGroupMembers(g.toEntityMembers)

      case DbState.Project(p) =>
        val np = DocumentMerger[ProjectCreated]
          .create(projectCreated)
          .get
          .asInstanceOf[ProjectDocument]
        np.copy(version = p.version, owners = p.owners, members = p.members)

      case DbState.PartialProject(p) =>
        p.applyTo(
          DocumentMerger[ProjectCreated]
            .create(projectCreated)
            .get
            .asInstanceOf[EntityDocument]
        )

    def checkExpected(doc: SolrDocument): Boolean =
      doc match
        case p: ProjectDocument =>
          val expect = expectedProject.setVersion(DocVersion.Off)
          p.setVersion(DocVersion.Off) == expect
        case _ => false

    override def toString = s"ProjectCreated: ${projectId.value.take(6)}… db=$dbState"

  val testCases =
    val proj = SolrDocumentGenerators.projectDocumentGen.generateOne
    val pproj = SolrDocumentGenerators.partialProjectGen.generateOne
    val group = SolrDocumentGenerators.entityMembersGen
      .flatMap(em => SolrDocumentGenerators.groupDocumentGen.map(g => g.setMembers(em)))
      .generateOne
    val dbState =
      List(
        DbState.Empty,
        DbState.Project(proj),
        DbState.PartialProject(pproj),
        DbState.Group(group)
      )
    dbState.map(TestCase.apply)
