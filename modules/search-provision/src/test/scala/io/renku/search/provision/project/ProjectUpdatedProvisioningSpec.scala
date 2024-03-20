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

import scala.concurrent.duration.*

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef

import io.github.arainko.ducktape.*
import io.renku.avro.codec.encoders.all.given
import io.renku.events.EventsGenerators.*
import io.renku.events.v1.{ProjectCreated, ProjectUpdated}
import io.renku.queue.client.Generators.messageHeaderGen
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.documents.{CompoundId, EntityDocument}
import munit.CatsEffectSuite

class ProjectUpdatedProvisioningSpec extends ProvisioningSuite:

  (nameUpdate :: slugUpdate :: repositoriesUpdate :: visibilityUpdate :: descUpdate :: noUpdate :: Nil)
    .foreach { case TestCase(name, updateF) =>
      test(s"can fetch events, decode them, and update in Solr in case of $name"):
        withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
          for
            solrDocs <- SignallingRef.of[IO, Set[EntityDocument]](Set.empty)

            provisioningFiber <- handlers.projectUpdated.compile.drain.start

            created = projectCreatedGen(prefix = "update").generateOne
            _ <- solrClient.insert(Seq(created.toSolrDocument.widen))

            updated = updateF(created)
            _ <- queueClient.enqueue(
              queueConfig.projectUpdated,
              messageHeaderGen(ProjectUpdated.SCHEMA$).generateOne,
              updated
            )

            docsCollectorFiber <-
              Stream
                .awakeEvery[IO](500 millis)
                .evalMap(_ =>
                  solrClient.findById[EntityDocument](
                    CompoundId.projectEntity(Id(created.id))
                  )
                )
                .evalMap(_.fold(().pure[IO])(e => solrDocs.update(_ => Set(e))))
                .compile
                .drain
                .start

            _ <- solrDocs.waitUntil(
              _ contains created.update(updated).toSolrDocument
            )

            _ <- provisioningFiber.cancel
            _ <- docsCollectorFiber.cancel
          yield ()
        }
    }

  private case class TestCase(name: String, f: ProjectCreated => ProjectUpdated)
  private lazy val nameUpdate = TestCase(
    "name update",
    pc =>
      ProjectUpdated(
        pc.id,
        stringGen(max = 5).generateOne,
        pc.slug,
        pc.repositories,
        pc.visibility,
        pc.description
      )
  )
  private lazy val slugUpdate = TestCase(
    "slug update",
    pc =>
      ProjectUpdated(
        pc.id,
        pc.name,
        stringGen(max = 5).generateOne,
        pc.repositories,
        pc.visibility,
        pc.description
      )
  )
  private lazy val repositoriesUpdate = TestCase(
    "repositories update",
    pc =>
      ProjectUpdated(
        pc.id,
        pc.name,
        pc.slug,
        stringGen(max = 5).generateList,
        pc.visibility,
        pc.description
      )
  )
  private lazy val visibilityUpdate = TestCase(
    "repositories update",
    pc =>
      ProjectUpdated(
        pc.id,
        pc.name,
        pc.slug,
        pc.repositories,
        projectVisibilityGen.generateOne,
        pc.description
      )
  )
  private lazy val descUpdate = TestCase(
    "repositories update",
    pc =>
      ProjectUpdated(
        pc.id,
        pc.name,
        pc.slug,
        pc.repositories,
        pc.visibility,
        stringGen(max = 5).generateSome
      )
  )
  private lazy val noUpdate = TestCase(
    "no update",
    _.to[ProjectUpdated]
  )

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
