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

import munit.Suite
import io.renku.servers.RedisServer
import munit.AnyFixture
import io.renku.redis.client.*
import scala.concurrent.duration.*

/** Starts the redis server if not already running.
  *
  * This is here for running single tests from outside sbt. Within sbt, the solr server is
  * started before any test is run and therefore will live for the entire test run.
  */
trait RedisServerSuite extends Suite:

  private lazy val redisServerValue: RedisServer = RedisServer

  val redisServer: Fixture[RedisConfig] =
    new Fixture[RedisConfig]("redis-server"):
      private var redisConfig: RedisConfig = null
      def apply(): RedisConfig = redisConfig

      override def beforeAll(): Unit =
        redisServerValue.start()
        redisConfig = RedisConfig(
          RedisHost(redisServerValue.host),
          RedisPort(redisServerValue.port),
          connectionRefreshInterval = 10.minutes
        )

      override def afterAll(): Unit =
        redisServerValue.stop()

  override def munitFixtures: Seq[AnyFixture[?]] =
    super.munitFixtures ++ List(redisServer)
