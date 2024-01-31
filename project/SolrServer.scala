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

object SolrServer extends SolrServer("graph", port = 8983)

@annotation.nowarn()
class SolrServer(module: String, port: Int) {

  val url: String = s"http://localhost:$port"

  // When using a local Solr for development, use this env variable
  // to not start a Solr server via docker for the tests
  private val skipServer: Boolean = sys.env.contains("NO_SOLR")

  private val containerName = s"$module-test-solr"
  private val image = "solr:9.4.1-slim"
  val genericCoreName = "core-test"
  val searchCoreName = "search-core-test"
  private val cores = Set(genericCoreName, searchCoreName)
  private val startCmd = s"""|docker run --rm
                             |--name $containerName
                             |-p $port:8983
                             |-d $image""".stripMargin
  private val isRunningCmd =
    Seq("docker", "container", "ls", "--filter", s"name=$containerName")
  private val stopCmd = s"docker stop -t5 $containerName"
  private def readyCmd(core: String) =
    s"curl http://localhost:8983/solr/$core/select?q=*:* --no-progress-meter --fail 1> /dev/null"
  private def isReadyCmd(core: String) =
    Seq("docker", "exec", containerName, "sh", "-c", readyCmd(core))
  private def createCore(core: String) = s"precreate-core $core"
  private def createCoreCmd(core: String) =
    Seq("docker", "exec", containerName, "sh", "-c", createCore(core))
  private val wasStartedHere = new AtomicBoolean(false)

  def start(): Unit =
    if (skipServer) println("Not starting Solr via docker")
    else if (checkRunning) ()
    else {
      println(s"Starting Solr container for '$module' from '$image' image")
      startContainer()
      waitForCoresToBeReady()
    }

  private def waitForCoresToBeReady(): Unit = {
    var rc = 1
    val maxTries = 500
    var counter = 0
    while (rc != 0 && counter < maxTries) {
      counter += 1
      Thread.sleep(500)
      rc = checkCoresReady
      if (rc == 0) println(s"Solr container for '$module' ready on port $port")
    }
    if (rc != 0) sys.error("Solr container for '$module' could not be started")
  }

  private def checkCoresReady =
    cores.foldLeft(0)((rc, core) => if (rc == 0) isReadyCmd(core).! else rc)

  private def checkRunning: Boolean = {
    val out = isRunningCmd.lineStream_!.take(20).toList
    val isRunning = out.exists(_ contains containerName)
    if (isRunning) waitForCoresToBeReady()
    isRunning
  }

  private def startContainer(): Unit = {
    val retryOnContainerFailedToRun: Throwable => Unit = {
      case ex if ex.getMessage contains "Nonzero exit value: 125" =>
        Thread.sleep(500); start()
      case ex => throw ex
    }
    Try(startCmd.!!).fold(retryOnContainerFailedToRun, _ => wasStartedHere.set(true))
    val rcs = cores.map(c => c -> createCoreCmd(c).!)
    println(
      s"Created solr cores: ${rcs.map { case (core, rc) => s"'$core' ($rc)" }.mkString(", ")}"
    )
  }

  def stop(): Unit =
    if (!skipServer && !wasStartedHere.get()) ()
    else {
      println(s"Stopping Solr container for '$module'")
      stopCmd.!!
      ()
    }

  def forceStop(): Unit =
    if (!skipServer) {
      println(s"Stopping Solr container for '$module'")
      stopCmd.!!
      ()
    }
}
