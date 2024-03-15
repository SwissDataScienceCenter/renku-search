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

package io.renku.search.provision

import cats.effect.*
import cats.syntax.all.*

import io.renku.logging.LoggingSetup
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.migration.SchemaMigrator

object Microservice extends IOApp:
  private val logger = scribe.cats.io

  override def run(args: List[String]): IO[ExitCode] =
    Services.make[IO].use { services =>
      for {
        _ <- IO(LoggingSetup.doConfigure(services.config.verbosity))
        _ <- runSolrMigrations(services.config)
        pm <- BackgroundProcessManage[IO](services.config.retryOnErrorDelay)
        tasks = services.messageHandlers.getAll.toList
        _ <- tasks.traverse_(pm.register.tupled)
        _ <- pm.startAll
      } yield ExitCode.Success
    }

  def runSolrMigrations(cfg: SearchProvisionConfig): IO[Unit] =
    SchemaMigrator[IO](cfg.solrConfig)
      .use(_.migrate(Migrations.all))
      .handleErrorWith { err =>
        logger.error("Running solr migrations failure, retrying", err) >>
          Temporal[IO].delayBy(runSolrMigrations(cfg), cfg.retryOnErrorDelay)
      }
