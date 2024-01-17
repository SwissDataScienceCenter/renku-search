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
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import dev.profunktor.redis4cats.effect.MkRedis.forAsync
import dev.profunktor.redis4cats.{Redis, RedisCommands}

trait RedisSpec:
  self: munit.Suite =>

  private lazy val server: RedisServer = RedisServer

  type Command = RedisCommands[IO, String, String]

  val withRedis: Fixture[Resource[IO, Command]] =
    new Fixture[Resource[IO, Command]]("redis") {

      def apply(): Resource[IO, Command] =
        Redis[IO].utf8(server.url)

      override def beforeAll(): Unit =
        server.start()

      override def afterAll(): Unit =
        server.stop()
    }

  override def munitFixtures: Seq[Fixture[Resource[IO, Command]]] = List(withRedis)
