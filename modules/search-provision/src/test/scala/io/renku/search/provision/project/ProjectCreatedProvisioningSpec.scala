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
import io.renku.events.EventsGenerators.projectCreatedGen
import io.renku.search.events.ProjectCreated
import io.renku.queue.client.DataContentType
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.model.ModelGenerators
import io.renku.search.provision.events.syntax.*
import io.renku.search.provision.{BackgroundCollector, ProvisioningSuite}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.documents.{Project as ProjectDocument, *}
import io.renku.search.provision.handler.DocumentMerger
import io.renku.solr.client.DocVersion
import io.renku.queue.client.SchemaVersion

class ProjectCreatedProvisioningSpec extends ProvisioningSuite:

  ProjectCreatedProvisioningSpec.testCases.foreach { tc =>
    test(s"processes message and update solr: $tc"):
      withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
        for {
          _ <- tc.dbState.create(solrClient)

          collector <- BackgroundCollector[SolrDocument](
            loadPartialOrEntity(solrClient, tc.projectId)
          )
          _ <- collector.start

          provisioningFiber <- handlers.projectCreated.compile.drain.start

          _ <- queueClient.enqueue(
            queueConfig.projectCreated,
            messageHeaderGen(
              tc.projectCreated.schema,
              DataContentType.Binary
            ).generateOne
              .copy(schemaVersion = SchemaVersion(tc.projectCreated.version.name)),
            tc.projectCreated
          )
          _ <- collector.waitUntil(docs =>
            scribe.info(s"Check for ${tc.expectedProject}")
            docs.exists(tc.checkExpected)
          )

          _ <- provisioningFiber.cancel
        } yield ()
      }
  }

  test("can fetch events binary encoded, decode them, and send them to Solr"):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        provisioningFiber <- handlers.projectCreated.compile.drain.start

        created = projectCreatedGen(prefix = "binary").generateOne
        _ <- queueClient.enqueue(
          queueConfig.projectCreated,
          messageHeaderGen(created.schema, DataContentType.Binary).generateOne
            .copy(schemaVersion = SchemaVersion(created.version.name)),
          created
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
    }

  test("can fetch events JSON encoded, decode them, and send them to Solr"):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        provisioningFiber <- handlers.projectCreated.compile.drain.start

        created = projectCreatedGen(prefix = "json").generateOne
        _ <- queueClient.enqueue(
          queueConfig.projectCreated,
          messageHeaderGen(created.schema, DataContentType.Json).generateOne
            .copy(schemaVersion = SchemaVersion(created.version.name)),
          created
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
    }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)

object ProjectCreatedProvisioningSpec:
  enum DbState:
    case Empty
    case Project(project: ProjectDocument)
    case PartialProject(project: PartialEntityDocument.Project)

    def create(solrClient: SearchSolrClient[IO]) = this match
      case DbState.Empty             => IO.unit
      case DbState.Project(p)        => solrClient.upsertSuccess(Seq(p))
      case DbState.PartialProject(p) => solrClient.upsertSuccess(Seq(p))

  case class TestCase(dbState: DbState):
    val projectId = dbState match
      case DbState.Empty             => ModelGenerators.idGen.generateOne
      case DbState.Project(p)        => p.id
      case DbState.PartialProject(p) => p.id

    val projectCreated: ProjectCreated =
      projectCreatedGen("test-").generateOne.withId(projectId)

    val expectedProject: SolrDocument = dbState match
      case DbState.Empty =>
        DocumentMerger[ProjectCreated].create(projectCreated).get

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
    val dbState =
      List(DbState.Empty, DbState.Project(proj), DbState.PartialProject(pproj))
    dbState.map(TestCase.apply)
