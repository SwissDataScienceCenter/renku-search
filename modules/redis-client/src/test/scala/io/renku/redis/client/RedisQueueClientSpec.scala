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

package io.renku.redis.client

import cats.effect.IO
import fs2.*
import fs2.concurrent.SignallingRef
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.util.RedisSpec
import munit.CatsEffectSuite

import java.nio.charset.StandardCharsets.UTF_8

class RedisQueueClientSpec extends CatsEffectSuite with RedisSpec:

  test("can enqueue and dequeue events") {
    withRedisClient.asQueueClient().use { client =>
      val queue = RedisClientGenerators.queueNameGen.generateOne
      for
        dequeued <- SignallingRef.of[IO, List[String]](Nil)

        message1 = "message1"
        _ <- client.enqueue(queue, message1.getBytes)

        fiber <- client
          .acquireEventsStream(queue, chunkSize = 1)
          .evalMap(m => dequeued.update(new String(m, UTF_8) :: _))
          .compile
          .drain
          .start
        _ <- dequeued.waitUntil(_ == List(message1))

        message2 = "message2"
        _ <- client.enqueue(queue, message2.getBytes)
        _ <- dequeued.waitUntil(_.toSet == Set(message1, message2))

        _ <- fiber.cancel
      yield ()
    }
  }
