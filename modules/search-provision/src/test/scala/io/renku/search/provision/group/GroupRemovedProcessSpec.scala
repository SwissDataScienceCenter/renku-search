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

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.Id
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.{
  CompoundId,
  EntityDocument,
  Group as GroupDocument,
  PartialEntityDocument,
  SolrDocument
}
import io.renku.solr.client.DocVersion

import scala.concurrent.duration.*
import io.renku.events.EventsGenerators
import org.scalacheck.Gen
import io.renku.search.events.GroupRemoved

class GroupRemovedProcessSpec extends ProvisioningSuite:

  test(
    "can fetch events, decode them, remove the Group from Solr, " +
      "and turn all the group's project to partial in Solr"
  ):
    withMessageHandlers(queueConfig).use { case (handlers, queueClient, solrClient) =>
      for
        groupSolrDoc <- SignallingRef.of[IO, Option[EntityDocument]](None)
        projSolrDoc <- SignallingRef.of[IO, Option[SolrDocument]](None)

        provisioningFiber <- handlers.groupRemoved.compile.drain.start

        group = EventsGenerators
          .groupAddedGen(prefix = "group-removal")
          .generateOne
          .fold(_.toModel(DocVersion.Off))
        project = projectDocumentGen.generateOne.copy(namespace = group.namespace.some)
        _ <- solrClient.upsert(Seq(group, project))

        groupDocsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ =>
              solrClient.findById[EntityDocument](CompoundId.groupEntity(group.id))
            )
            .evalMap(e => groupSolrDoc.update(_ => e))
            .compile
            .drain
            .start
        projectDocsCollectorFiber <-
          Stream
            .awakeEvery[IO](500 millis)
            .evalMap(_ =>
              solrClient
                .findById[EntityDocument](CompoundId.projectEntity(project.id))
                .flatMap {
                  case None =>
                    solrClient.findById[PartialEntityDocument](
                      CompoundId.projectPartial(project.id)
                    )
                  case e => e.pure[IO]
                }
            )
            .evalMap(e => projSolrDoc.update(_ => e))
            .compile
            .drain
            .start

        _ <- groupSolrDoc.waitUntil(_.nonEmpty)
        _ <- projSolrDoc.waitUntil(_.exists(_.isInstanceOf[EntityDocument]))

        _ <- queueClient.enqueue(
          queueConfig.groupRemoved,
          EventsGenerators.eventMessageGen(Gen.const(GroupRemoved(group.id))).generateOne
        )

        _ <- groupSolrDoc.waitUntil(_.isEmpty)
        _ <- projSolrDoc.waitUntil(_.exists(_.isInstanceOf[PartialEntityDocument]))

        _ <- provisioningFiber.cancel
        _ <- groupDocsCollectorFiber.cancel
        _ <- projectDocsCollectorFiber.cancel
      yield ()
    }

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient, withSearchSolrClient)
