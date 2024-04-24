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

package io.renku.search.provision.handler

import scala.concurrent.duration.*

import cats.Show
import cats.data.OptionT
import cats.effect.IO
import cats.effect.Ref
import fs2.Stream

import io.renku.avro.codec.all.given
import io.renku.events.v1.UserAdded
import io.renku.queue.client.Generators as QueueGenerators
import io.renku.search.events.MessageId
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.ModelGenerators.idGen
import io.renku.search.solr.client.SearchSolrSuite
import io.renku.search.solr.documents.User as UserDocument
import io.renku.solr.client.DocVersion
import io.renku.solr.client.UpsertResponse
import org.scalacheck.Gen
import scribe.Scribe

class PushToSolrSpec extends SearchSolrSuite:

  val messageGen: Gen[MessageReader.Message[UserDocument]] =
    for
      userId <- idGen
      added = UserAdded(userId.value, None, None, None)
      raw <- QueueGenerators.queueMessageGen(UserAdded.SCHEMA$, added)
      doc = UserDocument(userId)
    yield MessageReader.Message(raw, Seq(doc))

  def pushData(solr: PushToSolr[IO], runBefore: IO[Unit])(
      msg: MessageReader.Message[EntityOrPartial],
      retries: Int
  ): Stream[IO, UpsertResponse] =
    val retry: OptionT[IO, Stream[IO, UpsertResponse]] =
      OptionT.when(retries > 0)(pushData(solr, runBefore)(msg, retries - 1))
    Stream.eval(runBefore).drain ++ Stream
      .emit(msg)
      .through(solr.push(onConflict = retry, maxWait = 10.millis))

  test("honor max retries on conflict"):
    val reader = PushToSolrSpec.MessageReaderMock()
    withSearchSolrClient().use { client =>
      val pushToSolr = PushToSolr[IO](client, reader)
      val counter = Ref.unsafe[IO, Int](0)
      val msg =
        messageGen.generateOne.map(_.setVersion(DocVersion.Exists): EntityOrPartial)
      val maxRetries = 2
      for
        r <- pushData(pushToSolr, counter.update(_ + 1))(
          msg,
          maxRetries
        ).compile.lastOrError
        runs <- counter.get
        _ = assertEquals(runs, maxRetries + 1)
        _ = assertEquals(r, UpsertResponse.VersionConflict)
        marked <- reader.getProcessed
        _ = assert(marked.contains(msg.id))
      yield ()
    }

  test("try until success"):
    val reader = PushToSolrSpec.MessageReaderMock()
    withSearchSolrClient().use { client =>
      val pushToSolr = PushToSolr[IO](client, reader)
      val msg = messageGen.generateOne.map(_.setVersion(DocVersion.Exists))
      val counter = Ref.unsafe[IO, Int](0)
      val post = counter.updateAndGet(_ + 1).flatMap {
        case n if n == 3 =>
          scribe.cats.io.info(s"inserting now") >> client
            .upsertSuccess(msg.decoded.map(_.setVersion(DocVersion.Off)))
            .void
        case _ => IO.unit
      }
      val msgCast = msg.map(e => e: EntityOrPartial)
      val maxRetries = 6
      for
        r <- pushData(pushToSolr, post)(msgCast, maxRetries).compile.lastOrError
        runs <- counter.get
        _ = assertEquals(runs, 3)
        _ = assert(r.isSuccess)
        marked <- reader.getProcessed
        _ = assert(marked.contains(msg.id))
      yield ()
    }

object PushToSolrSpec:

  class MessageReaderMock extends MessageReader[IO]:
    private val processedIds: Ref[IO, Set[MessageId]] = Ref.unsafe(Set.empty)
    def getProcessed: IO[Set[MessageId]] = processedIds.get

    def read[A](using
        QueueMessageDecoder[IO, A],
        Show[A]
    ): Stream[IO, MessageReader.Message[A]] = ???
    def markProcessed(id: MessageId): IO[Unit] = processedIds.update(_ + id)
    def markProcessedError(err: Throwable, id: MessageId)(using
        logger: Scribe[IO]
    ): IO[Unit] = markProcessed(id)
