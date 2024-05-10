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

package io.renku.queue.client

import cats.effect.IO
import fs2.concurrent.SignallingRef

import io.renku.events.EventsGenerators
import io.renku.redis.client.RedisClientGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.*
import io.renku.search.events.EventMessage
import munit.CatsEffectSuite

class QueueClientSpec extends CatsEffectSuite with QueueSpec:
  test("can enqueue and dequeue project-member-add events"):
    withQueueClient().use { queue =>
      val qname = RedisClientGenerators.queueNameGen.generateOne
      val msg = EventsGenerators
        .eventMessageGen(EventsGenerators.projectMemberAddedGen)
        .generateOne
      for
        msgId <- queue.enqueue(qname, msg)
        res <- queue
          .acquireMessageStream[ProjectMemberAdded](qname, 1, None)
          .take(1)
          .compile
          .toList
        _ = assertEquals(res.head, msg.copy(id = msgId))
      yield ()
    }

  test("can enqueue and dequeue project-created events"):
    withQueueClient().use { queueClient =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      for
        dequeued <- SignallingRef.of[IO, List[EventMessage[ProjectCreated]]](Nil)

        message0 = EventsGenerators
          .eventMessageGen(EventsGenerators.projectCreatedGen("test"))
          .generateOne
        message1Id <- queueClient.enqueue(queue, message0)
        message1 = message0.copy(id = message1Id)

        streamingProcFiber <- queueClient
          .acquireMessageStream[ProjectCreated](queue, chunkSize = 1, maybeOffset = None)
          .evalMap(event => dequeued.update(event :: _))
          .compile
          .drain
          .start
        _ <- dequeued.waitUntil(_.contains(message1))

        _ <- streamingProcFiber.cancel
      yield ()
    }
