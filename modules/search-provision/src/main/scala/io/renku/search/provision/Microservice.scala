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

import cats.effect.{ExitCode, IO, IOApp, Temporal}
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.migration.SchemaMigrator
import scribe.Scribe
import scribe.cats.*

object Microservice extends IOApp:

  private val loadConfig: IO[SearchProvisionConfig] =
    SearchProvisionConfig.config.load[IO]

  override def run(args: List[String]): IO[ExitCode] =
    for {
      config <- loadConfig
      _ <- runSolrMigrations(config)
      _ <- startProvisioning(config)
    } yield ExitCode.Success

  private def startProvisioning(cfg: SearchProvisionConfig): IO[Unit] =
    SearchProvisioner[IO](cfg.queueName, cfg.redisConfig, cfg.solrConfig)
      .evalMap(_.provisionSolr.start)
      .use(_ => IO.never)
      .handleErrorWith { err =>
        Scribe[IO].error("Starting provisioning failure, retrying", err) >>
          Temporal[IO].delayBy(startProvisioning(cfg), cfg.retryOnErrorDelay)
      }

  private def runSolrMigrations(cfg: SearchProvisionConfig): IO[Unit] =
    SchemaMigrator[IO](cfg.solrConfig)
      .use(_.migrate(Migrations.all))
      .handleErrorWith { err =>
        Scribe[IO].error("Running solr migrations failure, retrying", err) >>
          Temporal[IO].delayBy(runSolrMigrations(cfg), cfg.retryOnErrorDelay)
      }
