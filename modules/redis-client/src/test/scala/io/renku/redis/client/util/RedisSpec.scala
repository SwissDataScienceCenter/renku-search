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

package io.renku.redis.client.util

import cats.effect.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import dev.profunktor.redis4cats.effect.MkRedis.forAsync
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import io.renku.queue.client.QueueClient
import io.renku.redis.client.RedisQueueClient

trait RedisSpec:
  self: munit.Suite =>

  private lazy val server: RedisServer = RedisServer

  abstract class RedisFixture extends Fixture[Resource[IO, RedisClient]]("redis"):
    def asRedisCommand(): Resource[IO, RedisCommands[IO, String, String]]
    def asQueueClient(): Resource[IO, QueueClient[IO]]

  val withRedisClient: RedisFixture = new RedisFixture:

    def apply(): Resource[IO, RedisClient] =
      RedisClient[IO].from(server.url)

    override def asRedisCommand(): Resource[IO, RedisCommands[IO, String, String]] =
      apply().flatMap(Redis[IO].fromClient(_, RedisCodec.Utf8))

    override def asQueueClient(): Resource[IO, QueueClient[IO]] =
      apply().map(new RedisQueueClient[IO](_))

    override def beforeAll(): Unit =
      server.start()

    override def afterAll(): Unit =
      server.stop()

  override def munitFixtures: Seq[Fixture[Resource[IO, RedisClient]]] =
    List(withRedisClient)
