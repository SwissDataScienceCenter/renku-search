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
import io.renku.search.model.Id
import io.renku.search.provision.MessageHandlers.MessageHandlerKey
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.TestServices
import io.renku.search.provision.reindex.ReprovisionService.ReprovisionRequest
import io.renku.search.solr.schema.EntityDocumentSchema
import io.renku.solr.client.*
import org.scalacheck.Gen

class ReprovisionServiceSpec extends ProvisioningSuite:
  type ManagementDoc = ReprovisionServiceImpl.ReprovisionManageDoc
  val mgmtDocId = ReprovisionServiceImpl.docId

  override val queueConfig: QueuesConfig =
    ProvisioningSuite.queueConfig.copy(dataServiceAllEvents =
      RedisClientGenerators.queueNameGen.generateOne
    )

  val allQuery: QueryData =
    QueryData(QueryString("_kind:*", 10, 0))
      .withSort(SolrSort(EntityDocumentSchema.Fields.id -> SolrSort.Direction.Asc))

  def idQuery(id: Id, more: Id*): QueryData =
    val idsq = (id +: more).map(i => s"id:$i").mkString(" OR ")
    QueryData(QueryString(s"_kind:* AND ($idsq)", more.size + 1, 0))
      .withSort(SolrSort(EntityDocumentSchema.Fields.id -> SolrSort.Direction.Asc))

  // must cancel all handlers after each test case
  def reset: Resource[IO, TestServices] =
    Resource.make(
      IO(testServices())
        .flatTap(s => truncateAll(s.searchClient.underlying)(Seq.empty, Seq.empty))
        .flatTap(_ => redisClearAll)
    )(_.backgroundManage.cancelProcesses(_ => true))

  test("reprovision started sets a document with correct messsage id"):
    reset.use { services =>
      for
        _ <- services.backgroundManage.register(
          MessageHandlerKey.DataServiceAllEvents,
          services.syncHandler(queueConfig.dataServiceAllEvents).create.compile.drain
        )
        _ <- services.backgroundManage.startAll

        // no management document
        _ <- services.searchClient.underlying
          .findById[ManagementDoc](mgmtDocId)
          .assert(_.responseBody.docs.isEmpty)

        // set two projects
        proj1 <- IO(EventsGenerators.projectCreatedGen("p1").generateOne)
        proj2 <- IO(EventsGenerators.projectCreatedGen("p2").generateOne)
        query = idQuery(proj1.id, proj2.id)
        msg1 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj1)).generateOne)
        msg2 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj2)).generateOne)
        mId1 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg1)
        mId2 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg2)

        docs <- waitForSolrDocs(services, query, _.size >= 2)
        _ = assertEquals(docs.size, 2)

        r <- services.reprovision.reprovision(
          ReprovisionRequest.Started(mId1, Id("repro-id"))
        )
        _ = assert(r)

        docsN <- waitForSolrDocs(services, query, _.size >= 1)
        _ = assertEquals(docsN.size, 1)

        // management document
        _ <- services.searchClient.underlying
          .findById[ManagementDoc](mgmtDocId)
          .assert { resp =>
            resp.responseBody.docs.size == 1 &&
            resp.responseBody.docs.head.messageId == mId1
          }
      yield ()
    }

  test("receiving a past message id, ignore reprovisioning"):
    reset.use { services =>
      for
        _ <- services.backgroundManage.register(
          MessageHandlerKey.DataServiceAllEvents,
          services.syncHandler(queueConfig.dataServiceAllEvents).create.compile.drain
        )
        _ <- services.backgroundManage.startAll

        // no management document
        _ <- services.searchClient.underlying
          .findById[ManagementDoc](mgmtDocId)
          .assert(_.responseBody.docs.isEmpty)

        // set two projects
        proj1 <- IO(EventsGenerators.projectCreatedGen("p3").generateOne)
        proj2 <- IO(EventsGenerators.projectCreatedGen("p4").generateOne)
        query = idQuery(proj1.id, proj2.id)
        msg1 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj1)).generateOne)
        msg2 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj2)).generateOne)
        mId1 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg1)
        mId2 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg2)

        _ <- waitForSolrDocs(services, query, _.size >= 2).assert(_.size == 2)

        r <- services.reprovision.reprovision(
          ReprovisionRequest.Started(mId2, Id("repro-id"))
        )
        _ = assert(r)

        _ <- waitForSolrDocs(services, query, _.isEmpty).assert(_.size == 0)

        r2 <- services.reprovision.reprovision(
          ReprovisionRequest.Started(mId1, Id("repro-id"))
        )
        _ = assert(!r2)

        // management document is unchanged
        _ <- services.searchClient.underlying
          .findById[ManagementDoc](mgmtDocId)
          .assert { resp =>
            resp.responseBody.docs.size == 1 &&
            resp.responseBody.docs.head.messageId == mId2
          }
      yield ()
    }

  test("from last start uses stored message id"):
    reset.use { services =>
      for
        _ <- services.backgroundManage.register(
          MessageHandlerKey.DataServiceAllEvents,
          services.syncHandler(queueConfig.dataServiceAllEvents).create.compile.drain
        )
        _ <- services.backgroundManage.startAll

        // no management document
        _ <- services.searchClient.underlying
          .findById[ManagementDoc](mgmtDocId)
          .assert(_.responseBody.docs.isEmpty)

        // set two projects
        proj1 <- IO(EventsGenerators.projectCreatedGen("p5").generateOne)
        proj2 <- IO(EventsGenerators.projectCreatedGen("p6").generateOne)

        query = idQuery(proj1.id, proj2.id)

        msg1 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj1)).generateOne)
        msg2 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj2)).generateOne)
        mId1 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg1)
        mId2 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg2)

        _ <- waitForSolrDocs(services, query, _.size >= 2).assert(_.size == 2)
        r <- services.reprovision.reprovision(
          ReprovisionRequest.Started(mId2, Id("repro-id"))
        )
        _ = assert(r)
        _ <- waitForSolrDocs(services, query, _.isEmpty).assert(_.size == 0)

        // forcefully set a messageId, so it re-processes only the last one
        _ <- services.searchClient.underlying.upsertLoop[ManagementDoc, Unit](mgmtDocId) {
          doc =>
            (doc.map(_.copy(messageId = mId1)), ())
        }

        _ <- services.reprovision.recreateIndex.assertEquals(true)

        _ <- waitForSolrDocs(services, query, _.size >= 1).assert(_.size == 1)
      yield ()
    }

  test("process reprovisioning started message"):
    reset.use { services =>
      for
        proj1 <- IO(EventsGenerators.projectCreatedGen("p7").generateOne)
        proj2 <- IO(EventsGenerators.projectCreatedGen("p8").generateOne)
        msg1 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj1)).generateOne)
        msg2 <- IO(
          EventsGenerators
            .eventMessageGen(EventsGenerators.reprovisionStarted())
            .generateOne
        )
        msg3 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj2)).generateOne)
        mId1 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg1)
        mId2 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg2)
        mId3 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg3)

        _ <- services.backgroundManage.register(
          MessageHandlerKey.DataServiceAllEvents,
          services.syncHandler(queueConfig.dataServiceAllEvents).create.compile.drain
        )
        _ <- services.backgroundManage.startAll

        query = idQuery(proj1.id, proj2.id)
        docs <- waitForSolrDocs(services, query, _.map(_.id).contains(proj2.id))
        _ = assertEquals(docs.map(_.id), List(proj2.id))
      //  _ <- services.backgroundManage.cancelProcesses(_ => true)
      yield ()
    }

  test("skip previous reprovisioning started messages"):
    reset.use { services =>
      for
        // SETUP
        // set up 2 projects (proj1 + proj2), reset index and then add 1 project (proj3)

        proj1 <- IO(EventsGenerators.projectCreatedGen("p9").generateOne)
        proj2 <- IO(EventsGenerators.projectCreatedGen("p10").generateOne)
        proj3 <- IO(EventsGenerators.projectCreatedGen("p11").generateOne)

        msg1 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj1)).generateOne)
        msg2 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj2)).generateOne)
        msg3 <- IO(
          EventsGenerators
            .eventMessageGen(EventsGenerators.reprovisionStarted())
            .generateOne
        )
        msg4 <- IO(EventsGenerators.eventMessageGen(Gen.const(proj3)).generateOne)

        mId1 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg1)
        mId2 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg2)
        mId3 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg3)
        mId4 <- services.queueClient.enqueue(queueConfig.dataServiceAllEvents, msg4)

        _ <- services.backgroundManage.register(
          MessageHandlerKey.DataServiceAllEvents,
          services.syncHandler(queueConfig.dataServiceAllEvents).create.compile.drain
        )
        _ <- services.backgroundManage.startAll

        query = idQuery(proj1.id, proj2.id, proj3.id)
        docs <- waitForSolrDocs(
          services,
          query,
          d => d.map(_.id).contains(proj3.id)
        )

        // when running re-index from first message (so starts with
        // processing the second msg), it should create proj2, ignore
        // the already processed reprovision-started msg and then create
        // proj3 again
        _ <- services.reindex.startReIndex(Some(mId1))
        docs2 <- waitForSolrDocs(
          services,
          query,
          d => d.map(_.id).toSet == Set(proj2.id, proj3.id)
        )
        _ = assertEquals(docs2.map(_.id).toSet, Set(proj3.id, proj2.id))
      // _ <- services.backgroundManage.cancelProcesses(_ => true)
      yield ()
    }
