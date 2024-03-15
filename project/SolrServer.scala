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
import scala.annotation.tailrec
import scala.sys.process.*
import scala.util.Try

object SolrServer extends SolrServer("graph", None)

@annotation.nowarn()
class SolrServer(module: String, solrPort: Option[Int]) {

  private val port =
    solrPort.orElse(sys.env.get("RS_SOLR_PORT").map(_.toInt)).getOrElse(8983)
  private val host: String = sys.env.get("RS_SOLR_HOST").getOrElse("localhost")
  val url: String = s"http://$host:$port"

  // When using a local Solr for development, use this env variable
  // to not start a Solr server via docker for the tests
  private val skipServer: Boolean = sys.env.contains("NO_SOLR")

  private val containerName = s"$module-test-solr"
  private val image = "solr:9.4.1-slim"
  val searchCoreName = "search-core-test"
  val testCoreName1 = "core-test1"
  val testCoreName2 = "core-test2"
  val testCoreName3 = "core-test3"
  private val cores = Set(testCoreName1, testCoreName2, testCoreName3, searchCoreName)
  private val startCmd = s"""|docker run --rm
                             |--name $containerName
                             |-p $port:8983
                             |-d $image""".stripMargin
  private val isRunningCmd =
    Seq("docker", "container", "ls", "--filter", s"name=$containerName")
  private val stopCmd = s"docker stop -t5 $containerName"
  private def readyCmd(core: String) =
    s"curl http://localhost:8983/solr/$core/select?q=*:* --no-progress-meter --fail 1> /dev/null"
  private def isCoreReadyCmd(core: String) =
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
      if (rc == 0) println(s"Solr cores for '$module' ready on port $port")
    }
    if (rc != 0) sys.error(s"Solr cores for '$module' could not be started")
  }

  private def checkCoresReady =
    cores.foldLeft(0)((rc, core) => if (rc == 0) isCoreReadyCmd(core).! else rc)

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
    Thread.sleep(500)
    createCores(cores)
  }

  @tailrec
  private def createCores(cores: Set[String], attempt: Int = 1): Unit = {
    if (attempt > 10)
      sys.error(
        s"Solr core(s) ${cores.map(c => s"'$c'").mkString(", ")} couldn't be created"
      )

    val rcs = cores.map(c => c -> createCoreCmd(c).!)

    val toRetry = rcs.foldLeft(Set.empty[String]) {
      case (toRetry, (c, 0)) =>
        println(s"Solr '$c' core created")
        toRetry
      case (toRetry, (c, rc)) =>
        println(s"Solr '$c' core creation failed $rc")
        toRetry + c
    }

    if (toRetry.nonEmpty) createCores(toRetry)
  }

  def stop(): Unit =
    if (skipServer || !wasStartedHere.get()) ()
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
