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

import scala.concurrent.duration.*

import io.renku.redis.client.*
import io.renku.servers.RedisServer
import munit.Fixture

/** Starts the redis server if not already running.
  *
  * This is here for running single tests from outside sbt. Within sbt, the solr server is
  * started before any test is run and therefore will live for the entire test run.
  */
trait RedisServerSuite:

  private lazy val redisServerValue = RedisServer

  val redisServer: Fixture[RedisConfig] =
    new Fixture[RedisConfig]("redis-server"):
      private var redisConfig: Option[RedisConfig] = None
      def apply(): RedisConfig = redisConfig match
        case Some(c) => c
        case None    => sys.error(s"Fixture $fixtureName not initialized")

      override def beforeAll(): Unit =
        redisServerValue.start()
        redisConfig = Some(
          RedisConfig(
            RedisHost(redisServerValue.host),
            RedisPort(redisServerValue.port),
            connectionRefreshInterval = 10.minutes
          )
        )

      override def afterAll(): Unit =
        redisServerValue.stop()
        redisConfig = None
