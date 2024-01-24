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

package io.renku.solr.client.util

import cats.syntax.all.*
import org.http4s.Uri

import java.util.concurrent.atomic.AtomicBoolean
import scala.sys.process.*
import scala.util.Try

object SolrServer extends SolrServer("graph", port = 8983)

class SolrServer(module: String, port: Int) {

  val url: Uri = Uri.unsafeFromString(s"http://localhost:$port")

  // When using a local Solr for development, use this env variable
  // to not start a Solr server via docker for the tests
  private val skipServer: Boolean = sys.env.contains("NO_SOLR")

  private val containerName = s"$module-test-solr"
  private val image = "solr:9.4.1-slim"
  val coreName = "renku-search-test"
  private val startCmd = s"""|docker run --rm
                             |--name $containerName
                             |-p $port:8983
                             |-d $image""".stripMargin
  private val isRunningCmd = s"docker container ls --filter 'name=$containerName'"
  private val stopCmd = s"docker stop -t5 $containerName"
  private val readyCmd = "solr status"
  private val createCore = s"precreate-core $coreName"
  private val isReadyCmd = s"docker exec $containerName sh -c '$readyCmd'"
  private val createCoreCmd = s"docker exec $containerName sh -c '$createCore'"
  private val wasRunning = new AtomicBoolean(false)

  def start(): Unit = synchronized {
    if (skipServer) println("Not starting Solr via docker")
    else if (checkRunning) ()
    else {
      println(s"Starting Solr container for '$module' from '$image' image")
      startContainer()
      var rc = 1
      while (rc != 0) {
        Thread.sleep(500)
        rc = isReadyCmd.!
        if (rc == 0) println(s"Solr container for '$module' started on port $port")
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
    val rc = createCoreCmd.!
    println(s"Created solr core $coreName ($rc)")
  }

  def stop(): Unit =
    if (!skipServer && !wasRunning.get()) {
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
