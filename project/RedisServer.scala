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
package io.renku.servers

import java.util.concurrent.atomic.AtomicBoolean
import scala.sys.process.*
import scala.util.Try

object RedisServer extends RedisServer("graph", port = 6379)

@annotation.nowarn()
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
  private val isRunningCmd =
    Seq("docker", "container", "ls", "--filter", s"name=$containerName")
  private val stopCmd = s"docker stop -t5 $containerName"
  private val readyCmd = "redis-cli -h 127.0.0.1 -p 6379 PING"
  private val isReadyCmd =
    Seq("docker", "exec", containerName, "sh", "-c", readyCmd)
  private val wasStartedHere = new AtomicBoolean(false)

  def start(): Unit = synchronized {
    if (skipServer) println("Not starting Redis via docker")
    else if (checkRunning) ()
    else {
      println(s"Starting Redis container for '$module' from '$image' image")
      startContainer()
      var rc = 1
      val maxTries = 500
      var counter = 0
      while (rc != 0 && counter < maxTries) {
        counter += 1
        Thread.sleep(500)
        rc = Process(isReadyCmd).!
        if (rc == 0) println(s"Redis container for '$module' started on port $port")
        else println(s"IsReadyCmd returned $rc")
      }
      if (rc != 0)
        sys.error(s"Redis container for '$module' could not be started on port $port")
    }
  }

  private def checkRunning: Boolean = {
    val out = isRunningCmd.lineStream_!.take(200).toList
    out.exists(_ contains containerName)
  }

  private def startContainer(): Unit = {
    val retryOnContainerFailedToRun: Throwable => Unit = {
      case ex if ex.getMessage contains "Nonzero exit value: 125" =>
        Thread.sleep(500); start()
      case ex => throw ex
    }
    Try(startCmd.!!).fold(retryOnContainerFailedToRun, _ => wasStartedHere.set(true))
  }

  def stop(): Unit =
    if (!skipServer && wasStartedHere.get()) {
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
