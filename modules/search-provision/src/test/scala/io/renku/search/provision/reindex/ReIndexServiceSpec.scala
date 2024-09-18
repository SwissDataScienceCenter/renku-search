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

package io.renku.search.provision.reindex

import cats.effect.*

import io.renku.events.EventsGenerators
import io.renku.redis.client.QueueName
import io.renku.redis.client.RedisClientGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.config.QueuesConfig
import io.renku.search.model.Name
import io.renku.search.provision.MessageHandlers.MessageHandlerKey
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.documents.{EntityDocument, Project as ProjectDocument}
import io.renku.search.solr.schema.EntityDocumentSchema
import io.renku.solr.client.*
import org.scalacheck.Gen

class ReIndexServiceSpec extends ProvisioningSuite:

  override val queueConfig: QueuesConfig =
    ProvisioningSuite.queueConfig.copy(dataServiceAllEvents =
      RedisClientGenerators.queueNameGen.generateOne
    )

  val allQuery: QueryData =
    QueryData(QueryString("_kind:*", 10, 0))
      .withSort(SolrSort(EntityDocumentSchema.Fields.id -> SolrSort.Direction.Asc))

  test("re-index restores data from redis stream"):
    for
      services <- IO(testServices())
      _ <- services.backgroundManage.register(
        MessageHandlerKey.DataServiceAllEvents,
        services.syncHandler(queueConfig.dataServiceAllEvents).create.compile.drain
      )
      _ <- services.backgroundManage.startAll
      proj1 <- IO(EventsGenerators.projectCreatedGen("p1").generateOne)
      proj2 <- IO(EventsGenerators.projectCreatedGen("p2").generateOne)
      msg1 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj1)).generateOne)
      msg2 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj2)).generateOne)
      mId1 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg1)
      mId2 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg2)

      docs <- waitForSolrDocs(services, allQuery, _.size == 2)
      _ = assertEquals(docs.size, 2)

      // corrupt the data at solr
      _ <- services.searchClient.upsert(
        Seq(docs.head.asInstanceOf[ProjectDocument].copy(name = Name("blaaah")))
      )
      changed <- services.searchClient
        .queryAll[EntityDocument](allQuery)
        .compile
        .toList
      _ = assertEquals(changed.size, 2)
      _ = assertNotEquals(changed, docs)

      // now run re-indexing from beginning when this returns,
      _ <- services.reindex.startReIndex(None)
      // re-indexing has been initiated, meaning that solr has been
      // cleared and background processes restarted. So only need to
      // wait for the 2 documents to reappear
      docs2 <- waitForSolrDocs(services, allQuery, _.size == 2)
      _ = assertEquals(docs2.size, 2)

      // afterwards, the initial state should be re-created
      _ = assertEquals(
        docs2.map(_.setVersion(DocVersion.Off)),
        docs.map(_.setVersion(DocVersion.Off))
      )
    yield ()

  test("re-index should not start when running already"):
    for
      services <- IO(testServices())

      lockDoc = ReIndexDocument.lockDocument[IO](None)
      doc <- lockDoc.acquire(None, ReIndexService.lockId)
      _ <- services.searchClient.upsertSuccess(Seq(doc))

      r <- services.reindex.startReIndex(None)
      _ = assertEquals(r, false)
    yield ()
