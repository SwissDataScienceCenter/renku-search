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

package io.renku.search.solr.client

import cats.effect.*
import cats.effect.std.CountDownLatch

import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.SolrClient
import io.renku.solr.client.migration.SchemaMigrator
import io.renku.solr.client.util.SolrClientBaseSuite

abstract class SearchSolrSuite extends SolrClientBaseSuite:

  val searchSolrR: Resource[IO, SearchSolrClient[IO]] =
    solrClientR
      .evalMap(c => SearchSolrSuite.setupSchema(c.config.core, c))
      .map(new SearchSolrClientImpl[IO](_))

  val searchSolr = ResourceFixture(searchSolrR)

object SearchSolrSuite:
  private val logger = scribe.cats.io
  private case class MigrateState(tasks: Map[String, IO[Unit]] = Map.empty):
    def add(name: String, task: IO[Unit]): MigrateState = copy(tasks.updated(name, task))
  private val currentState: Ref[IO, MigrateState] =
    Ref.unsafe(MigrateState())

  private def setupSchema(coreName: String, client: SolrClient[IO]): IO[Unit] =
    CountDownLatch[IO](1).flatMap { latch =>
      currentState.flatModify { state =>
        state.tasks.get(coreName) match
          case Some(t) =>
            (
              state,
              logger
                .info(s"Waiting for migrations to finish for core $coreName")
                .flatMap(_ => t)
            )
          case None =>
            val task = SchemaMigrator[IO](client)
              .migrate(Migrations.all)
              .flatTap(_ => latch.release)
            val wait = latch.await
            (state.add(coreName, wait), task)
      }
    }
