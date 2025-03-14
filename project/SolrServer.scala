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
import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.tailrec
import scala.sys.process.*
import scala.util.Try

@annotation.nowarn
object SolrServer {
  private val createCoreCounter: AtomicInteger = new AtomicInteger(0)

  private val port = sys.env.get("RS_SOLR_PORT").map(_.toInt).getOrElse(8983)
  private val host: String = sys.env.get("RS_SOLR_HOST").getOrElse("localhost")
  val url: String = s"http://$host:$port"

  // When using a local Solr for development, use this env variable
  // to not start a Solr server via docker for the tests
  private val skipServer: Boolean = sys.env.contains("NO_SOLR")

  private val containerName = "search-test-solr"
  private val image = "solr:9.4.1-slim"
  val searchCoreName = "search-core-test"
  private val cores = Set(searchCoreName)
  private val startCmd = s"""|docker run --rm
                             |--name $containerName
                             |-p $port:8983
                             |-d $image""".stripMargin
  private val isRunningCmd =
    Seq("docker", "container", "ls", "--filter", s"name=$containerName")
  private val stopCmd = s"docker stop -t5 $containerName"
  private def isCoreReadyCmd(core: String) =
    Seq(
      "curl",
      "--fail",
      "-s",
      "-o",
      "/dev/null",
      s"$url/solr/$core/select"
    )
  private def createCoreCmd(core: String) =
    sys.env
      .get("RS_SOLR_CREATE_CORE_CMD")
      .map(_.replace("%s", core))
      .getOrElse(s"docker exec $containerName solr create -c $core")
  private def deleteCoreCmd(core: String) =
    sys.env
      .get("RS_SOLR_DELETE_CORE_CMD")
      .map(_.replace("%s", core))
      .getOrElse(s"docker exec $containerName sh -c solr delete -c $core")

  // configsets are copied to $SOLR_HOME to allow core api to create cores
  private val copyConfigSetsCmd =
    Seq(
      "docker",
      "exec",
      containerName,
      "cp",
      "-r",
      "/opt/solr/server/solr/configsets",
      "/var/solr/data/"
    )

  private val wasStartedHere = new AtomicBoolean(false)

  def start(): Unit =
    if (skipServer) println("Not starting Solr via docker")
    else if (checkRunning) ()
    else {
      println(s"Starting Solr container '$image'")
      startContainer()
      createCores(cores)
      copyConfigSets()
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
      if (rc == 0) println(s"Solr cores ready on port $port")
    }
    if (rc != 0) sys.error(s"Solr cores could not be started")
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
        Thread.sleep(500); startContainer()
      case ex => throw ex
    }
    Try(startCmd.!!).fold(retryOnContainerFailedToRun, _ => wasStartedHere.set(true))
    Thread.sleep(500)
  }

  private def copyConfigSets(): Unit =
    copyConfigSetsCmd.!!

  def createCore(name: String): Try[Unit] = {
    val cmd = createCoreCmd(name)
    val n = createCoreCounter.incrementAndGet()
    println(s"Create $n-th core: $cmd")
    Try(cmd.!!).map(_ => ())
  }

  def deleteCore(name: String): Try[Unit] = {
    val cmd = deleteCoreCmd(name)
    println(s"Run delete core: $cmd")
    Try(cmd.!!).map(_ => ())
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
      println(s"Stopping Solr container '$image'")
      stopCmd.!!
      ()
    }

  def forceStop(): Unit =
    if (!skipServer) {
      println(s"Stopping Solr container '$image'")
      stopCmd.!!
      ()
    }
}
