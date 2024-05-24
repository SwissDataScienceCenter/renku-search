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

import cats.effect.IO
import fs2.Stream
import fs2.concurrent.SignallingRef

import io.renku.events.EventsGenerators
import io.renku.events.EventsGenerators.*
import io.renku.events.{v1, v2}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.{ProjectRemoved, SchemaVersion}
import io.renku.search.model.EntityType
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.query.Query
import io.renku.search.query.Query.Segment
import io.renku.search.query.Query.Segment.typeIs
import io.renku.search.solr.documents.{CompoundId, EntityDocument}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class ProjectRemovedProcessSpec extends ProvisioningSuite:

  test(s"can fetch events, decode them, and remove Solr"):
    for
      services <- IO(testServices())
      handler = services.messageHandlers
      queueClient = services.queueClient
      solrClient = services.searchClient

      solrDoc <- SignallingRef.of[IO, Option[EntityDocument]](None)

      provisioningFiber <- handler.projectRemoved.compile.drain.start

      created = projectCreatedGen(prefix = "remove").generateOne
      _ <- solrClient.upsert(Seq(created.toModel(DocVersion.Off).widen))

      docsCollectorFiber <-
        Stream
          .awakeEvery[IO](500 millis)
          .evalMap(_ =>
            solrClient.findById[EntityDocument](
              CompoundId.projectEntity(created.id)
            )
          )
          .evalMap(e => solrDoc.update(_ => e))
          .compile
          .drain
          .start

      _ <- solrDoc.waitUntil(
        _.nonEmpty
      )

      removed = ProjectRemoved(created.id)
      schemaVersion = Gen.oneOf(removed.version.toList).generateOne
      schema = schemaVersion match
        case SchemaVersion.V1 => v1.ProjectRemoved.SCHEMA$
        case SchemaVersion.V2 => v2.ProjectRemoved.SCHEMA$

      _ <- queueClient.enqueue(
        queueConfig.projectRemoved,
        EventsGenerators.eventMessageGen(Gen.const(removed)).generateOne
      )

      _ <- solrDoc.waitUntil(
        _.isEmpty
      )

      _ <- provisioningFiber.cancel
      _ <- docsCollectorFiber.cancel
    yield ()

  private lazy val queryProjects = Query(typeIs(EntityType.Project))
