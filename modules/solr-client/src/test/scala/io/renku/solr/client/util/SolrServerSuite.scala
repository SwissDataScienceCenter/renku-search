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

import io.renku.servers.SolrServer
import munit.Suite
import org.http4s.Uri

/** Starts the solr server if not already running.
  *
  * This is here for running single tests from outside sbt. Within sbt, the solr server is
  * started before any test is run and therefore will live for the entire test run.
  */
trait SolrServerSuite extends Suite:

  lazy val solrServerValue = SolrServer

  val solrServer: Fixture[Uri] =
    new Fixture[Uri]("solr-server"):
      private var serverUri: Option[Uri] = None
      def apply(): Uri = serverUri match
        case Some(u) => u
        case None    => sys.error(s"Fixture $fixtureName not initialized")

      override def beforeAll(): Unit =
        solrServerValue.start()
        serverUri = Some(Uri.unsafeFromString(solrServerValue.url))

      override def afterAll(): Unit =
        solrServerValue.stop()
