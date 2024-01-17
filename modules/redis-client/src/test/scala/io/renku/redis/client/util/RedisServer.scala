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

import cats.syntax.all._

import java.util.concurrent.atomic.AtomicBoolean
import scala.sys.process._
import scala.util.Try

object RedisServer extends RedisServer("graph", port = 6379)

class RedisServer(module: String, port: Int) {

  val url: String = s"redis://localhost:$port"

  // When using a local Redis for development, use this env variable
  // to not start a Redis server via docker for the tests
  private val skipServer: Boolean = sys.env.contains("NO_REDIS")

  private val containerName = s"$module-test-redis"
  private val image = "redis:7.2.4-alpine"
  private val startCmd = s"""|docker run --rm
                             |--name $containerName
                             |-p $port:6379
                             |-d $image""".stripMargin
  private val isRunningCmd = s"docker container ls --filter 'name=$containerName'"
  private val stopCmd = s"docker stop -t5 $containerName"
  private val readyCmd = "redis-cli -h 127.0.0.1 -p 6379 PING"
  private val isReadyCmd = s"docker exec $containerName sh -c '$readyCmd'"
  private val wasRunning = new AtomicBoolean(false)

  def start(): Unit = synchronized {
    if (skipServer) println("Not starting Redis via docker")
    else if (checkRunning) ()
    else {
      println(s"Starting Redis container for '$module' from '$image' image")
      startContainer()
      var rc = 1
      while (rc != 0) {
        Thread.sleep(500)
        rc = isReadyCmd.!
        if (rc == 0) println(s"Redis container for '$module' started on port $port")
      }
    }
  }

  private def checkRunning: Boolean = {
    val out = isRunningCmd.lazyLines.toList
    val isRunning = out.exists(_ contains containerName)
    wasRunning.set(isRunning)
    isRunning
  }

  private def startContainer(): Unit = {
    val retryOnContainerFailedToRun: Throwable => Unit = {
      case ex if ex.getMessage contains "Nonzero exit value: 125" =>
        Thread.sleep(500); start()
      case ex => throw ex
    }
    Try(startCmd.!!).fold(retryOnContainerFailedToRun, _ => ())
  }

  def stop(): Unit =
    if (!skipServer && !wasRunning.get()) {
      println(s"Stopping Redis container for '$module'")
      stopCmd.!!
      ()
    }

  def forceStop(): Unit =
    if (!skipServer) {
      println(s"Stopping Redis container for '$module'")
      stopCmd.!!
      ()
    }
}
