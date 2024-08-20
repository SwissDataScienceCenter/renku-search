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
import io.renku.events.EventsGenerators.*
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.ProjectRemoved
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
      handler = services.syncHandler(queueConfig.projectRemoved)
      queueClient = services.queueClient
      solrClient = services.searchClient

      created = projectCreatedGen(prefix = "remove").generateOne
      _ <- solrClient.upsert(Seq(created.toModel(DocVersion.Off).widen))
      initial <- solrClient.findById[EntityDocument](
        CompoundId.projectEntity(created.id)
      )
      _ = assert(initial.isDefined)

      removed = ProjectRemoved(created.id)

      _ <- queueClient.enqueue(
        queueConfig.projectRemoved,
        EventsGenerators.eventMessageGen(Gen.const(removed)).generateOne
      )

      _ <- handler.create.take(1).compile.drain

      found <- solrClient.findById[EntityDocument](
        CompoundId.projectEntity(created.id)
      )
      _ = assert(found.isEmpty)
    yield ()

  private lazy val queryProjects = Query(typeIs(EntityType.Project))
