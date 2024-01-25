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

import cats.effect.*
import io.renku.solr.client.{SolrClient, SolrConfig}

import scala.concurrent.duration.Duration

trait SolrSpec:
  self: munit.Suite =>

  private lazy val server: SolrServer = SolrServer

  val withSolrClient: Fixture[Resource[IO, SolrClient[IO]]] =
    new Fixture[Resource[IO, SolrClient[IO]]]("solr"):

      def apply(): Resource[IO, SolrClient[IO]] =
        SolrClient[IO](
          SolrConfig(server.url / "solr", server.coreName, Some(Duration.Zero), true)
        )

      override def beforeAll(): Unit =
        server.start()

      override def afterAll(): Unit =
        server.stop()

  override def munitFixtures: Seq[Fixture[Resource[IO, SolrClient[IO]]]] =
    List(withSolrClient)
