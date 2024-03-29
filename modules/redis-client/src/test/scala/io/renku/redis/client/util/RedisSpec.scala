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
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.Stdout.instance
import dev.profunktor.redis4cats.effect.MkRedis.forAsync
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import io.lettuce.core.RedisConnectionException
import io.renku.redis.client.*
import io.renku.servers.RedisServer

import scala.concurrent.duration.*

trait RedisSpec:
  self: munit.Suite =>

  export dev.profunktor.redis4cats.effect.Log.Stdout.instance

  private lazy val server: RedisServer = RedisServer

  abstract class RedisFixture extends Fixture[Resource[IO, RedisClient]]("redis"):
    def asRedisCommands(): Resource[IO, RedisCommands[IO, String, String]]
    def asRedisQueueClient(): Resource[IO, RedisQueueClient[IO]]
    def redisConfig: RedisConfig

  val withRedisClient: RedisFixture = new RedisFixture:

    def apply(): Resource[IO, RedisClient] =
      RedisClient[IO]
        .from(server.url)
        .recoverWith {
          case _: RedisConnectionException => apply()
          case ex => Resource.raiseError[IO, RedisClient, Throwable](ex)
        }

    override def asRedisCommands(): Resource[IO, RedisCommands[IO, String, String]] =
      apply().flatMap(createRedisCommands)

    override def asRedisQueueClient(): Resource[IO, RedisQueueClient[IO]] =
      RedisQueueClient.make[IO](redisConfig)

    override lazy val redisConfig: RedisConfig =
      RedisConfig(
        RedisHost(server.host),
        RedisPort(server.port),
        connectionRefreshInterval = 10 minutes
      )

    override def beforeAll(): Unit =
      server.start()

    override def afterAll(): Unit =
      server.stop()

  lazy val createRedisCommands
      : RedisClient => Resource[IO, RedisCommands[IO, String, String]] =
    Redis[IO].fromClient(_, RedisCodec.Utf8)

  override def munitFixtures: Seq[Fixture[?]] =
    List(withRedisClient)
