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

import io.renku.search.GeneratorSyntax.*
import io.renku.search.LoggingConfigure
import io.renku.solr.client.*
import munit.CatsEffectFixtures
import org.scalacheck.Gen

trait SolrClientBaseSuite
    extends SolrServerSuite
    with LoggingConfigure
    with SolrTruncate
    with CatsEffectFixtures:

  private val coreNameGen: Gen[String] =
    Gen
      .choose(5, 12)
      .flatMap(n => Gen.listOfN(n, Gen.alphaChar))
      .map(_.mkString)
      .map(name => s"test-core-$name")

  val solrClientR: Resource[IO, SolrClient[IO]] =
    for
      serverUri <- Resource.eval(IO(solrServer()))
      coreName <- Resource.eval(IO(coreNameGen.generateOne))
      cfg = SolrConfig(serverUri, coreName, None, false)
      client <- SolrClient[IO](cfg)
      _ <- Resource.make(createSolrCore(client, coreName))(_ =>
        deleteSolrCore(client, coreName).start.void
      )
    yield client

  val solrClient = ResourceSuiteLocalFixture("solr-client", solrClientR)

  def createSolrCore(client: SolrClient[IO], name: String): IO[Unit] =
    IO.blocking(solrServerValue.createCore(name).get)

  def deleteSolrCore(client: SolrClient[IO], name: String): IO[Unit] =
    IO.blocking(solrServerValue.deleteCore(name).get)
