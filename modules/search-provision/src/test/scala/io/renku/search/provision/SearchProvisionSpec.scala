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

package io.renku.search.provision

import cats.effect.{Clock, IO}
import fs2.*
import fs2.concurrent.SignallingRef
import io.renku.avro.codec.AvroIO
import io.renku.messages.ProjectCreated
import io.renku.avro.codec.encoders.all.given
import io.renku.avro.codec.decoders.all.given
import io.renku.redis.client.RedisClientGenerators
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.util.RedisSpec
import munit.CatsEffectSuite

import java.time.temporal.ChronoUnit

class SearchProvisionSpec extends CatsEffectSuite with RedisSpec:

  val avro = AvroIO(ProjectCreated.SCHEMA$)

  test("can enqueue and dequeue events"):
    withRedisClient.asQueueClient().use { client =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      for
        dequeued <- SignallingRef.of[IO, List[ProjectCreated]](Nil)

        now <- Clock[IO].realTimeInstant.map(_.truncatedTo(ChronoUnit.MILLIS))

        message1 = ProjectCreated("my project", "my description", Some("myself"), now)
        _ <- client.enqueue(queue, avro.write[ProjectCreated](Seq(message1)))

        streamingProcFiber <- client
          .acquireEventsStream(queue, chunkSize = 1, maybeOffset = None)
          .evalTap(m => IO.println(avro.read[ProjectCreated](m.payload)))
          .evalMap(event =>
            dequeued.update(avro.read[ProjectCreated](event.payload).toList ::: _)
          )
          .compile
          .drain
          .start
        _ <- dequeued.waitUntil(_ == List(message1))

        message2 = message1.copy(name = "my other project")
        _ <- client.enqueue(queue, avro.write(Seq(message2)))
        _ <- dequeued.waitUntil(_.toSet == Set(message1, message2))

        _ <- streamingProcFiber.cancel
      yield ()
    }
