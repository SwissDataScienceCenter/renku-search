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

package io.renku.search.provision.metrics

import cats.effect.IO

import io.prometheus.client.Collector
import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.EntityType
import io.renku.search.model.EntityType.User
import io.renku.search.solr.client.SearchSolrSuite
import io.renku.search.solr.client.SolrDocumentGenerators.userDocumentGen
import io.renku.search.solr.documents.DocumentKind

class DocumentKindGaugeUpdaterSpec extends SearchSolrSuite:

  test("update should fetch the data and insert 0 for missing kinds"):
    withSearchSolrClient().use { client =>
      val user = userDocumentGen.generateOne
      val gauge = TestGauge(User)

      val gaugeUpdater = new DocumentKindGaugeUpdater[IO](client, gauge)

      for {
        _ <- client.upsert(Seq(user.widen))
        _ <- gaugeUpdater.update()
      } yield assert {
        gauge.acc(DocumentKind.FullEntity) >= 1d &&
        gauge.acc(DocumentKind.PartialEntity) == 0d
      }
    }

  private class TestGauge(override val entityType: EntityType) extends DocumentKindGauge:
    var acc: Map[DocumentKind, Double] = Map.empty

    override def set(l: DocumentKind, v: Double): Unit =
      acc = acc + (l -> v)

    override def asJCollector: Collector = ???
