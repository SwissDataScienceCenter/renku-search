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

import cats.effect.{IO, Resource}
import dev.profunktor.redis4cats.connection.RedisClient
import io.renku.redis.client.util.RedisSpec

trait QueueSpec extends RedisSpec:
  self: munit.Suite =>

  abstract class QueueFixture extends Fixture[Resource[IO, QueueClient[IO]]]("queue")

  val withQueueClient: QueueFixture = () =>
    withRedisClient.asRedisQueueClient().map(new QueueClientImpl[IO](_))

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient, withQueueClient)
