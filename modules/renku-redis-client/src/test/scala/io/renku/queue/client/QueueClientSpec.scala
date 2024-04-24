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
import io.renku.avro.codec.AvroWriter
import io.renku.events.EventsGenerators
import io.renku.search.events.{MessageId, ProjectCreated}
import io.renku.queue.client.DataContentType.{Binary, Json}
import io.renku.queue.client.Generators.*
import io.renku.redis.client.{RedisClientGenerators}
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.search.GeneratorSyntax.*
import munit.CatsEffectSuite

class QueueClientSpec extends CatsEffectSuite with QueueSpec:

  test("can enqueue and dequeue events"):
    withQueueClient().use { queueClient =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      for
        dequeued <- SignallingRef.of[IO, List[QueueMessage]](Nil)

        message1 = EventsGenerators.projectCreatedGen("test").generateOne
        header1 = messageHeaderGen(message1.schema).generateOne
        message1Id <- queueClient.enqueue(queue, header1, message1)

        streamingProcFiber <- queueClient
          .acquireEventsStream(queue, chunkSize = 1, maybeOffset = None)
          .evalMap(event => dequeued.update(event :: _))
          .compile
          .drain
          .start
        _ <- dequeued.waitUntil(_ == List(toQueueMessage(message1Id, header1, message1)))

        _ <- streamingProcFiber.cancel
      yield ()
    }

  private def toQueueMessage(
      id: MessageId,
      header: MessageHeader,
      payload: ProjectCreated
  ) =
    val encodedPayload = header.dataContentType match {
      case Binary => AvroWriter(payload.schema).write(Seq(payload))
      case Json   => AvroWriter(payload.schema).writeJson(Seq(payload))
    }
    QueueMessage(id.value, header.toSchemaHeader(payload), encodedPayload)
