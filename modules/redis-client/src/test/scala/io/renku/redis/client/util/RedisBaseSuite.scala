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

import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log as RedisLog
import io.renku.redis.client.RedisQueueClient
import io.renku.redis.client.RedisQueueClientImpl
import io.renku.search.LoggingConfigure
import munit.*

abstract class RedisBaseSuite
    extends CatsEffectSuite
    with LoggingConfigure
    with RedisServerSuite:

  given RedisLog[IO] = new RedisLog {
    def debug(msg: => String): IO[Unit] = scribe.cats.io.debug(msg)
    def error(msg: => String): IO[Unit] = scribe.cats.io.error(msg)
    def info(msg: => String): IO[Unit] = scribe.cats.io.info(msg)
  }

  val redisClientsR: Resource[IO, RedisClients] =
    for
      config <- Resource.eval(IO(redisServer()))
      lc <- RedisClient[IO]
        .from(s"redis://${config.host.value}:${config.port.value}")
      cmds <- Redis[IO].fromClient(lc, RedisCodec.Utf8)
      qc = new RedisQueueClientImpl[IO](lc)
    yield RedisClients(config, lc, cmds, qc)

  val lowLevelClientR: Resource[IO, RedisClient] =
    redisClientsR.map(_.lowLevel)

  val redisCommandsR: Resource[IO, RedisCommands[IO, String, String]] =
    redisClientsR.map(_.commands)

  val redisQueueClientR: Resource[IO, RedisQueueClient[IO]] =
    redisClientsR.map(_.queueClient)

  val redisClients = ResourceSuiteLocalFixture("all-redis-clients", redisClientsR)

  val lowLevelClient =
    ResourceSuiteLocalFixture("low-level-redis-client", lowLevelClientR)

  val redisCommands =
    ResourceSuiteLocalFixture("redis-commands", redisCommandsR)

  val redisQueueClient =
    ResourceSuiteLocalFixture("redis-queue-client", redisQueueClientR)

  override def munitFixtures: Seq[AnyFixture[?]] =
    super.munitFixtures ++ List(
      redisClients,
      lowLevelClient,
      redisCommands,
      redisQueueClient
    )
