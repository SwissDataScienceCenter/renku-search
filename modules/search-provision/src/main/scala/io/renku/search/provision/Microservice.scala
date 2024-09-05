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

import scala.concurrent.duration.Duration

import cats.effect.*
import cats.syntax.all.*

import io.renku.logging.LoggingSetup
import io.renku.search.http.HttpServer
import io.renku.search.metrics.CollectorRegistryBuilder
import io.renku.search.provision.BackgroundProcessManage.TaskName
import io.renku.search.provision.metrics.*
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.migration.SchemaMigrator

object Microservice extends IOApp:

  private val logger = scribe.cats.io

  override def run(args: List[String]): IO[ExitCode] =
    Services.make[IO].use { services =>
      for {
        _ <- IO(LoggingSetup.doConfigure(services.config.verbosity))
        _ <- runSolrMigrations(services.config)
        registryBuilder = CollectorRegistryBuilder[IO].withJVMMetrics
          .add(RedisMetrics.queueSizeGauge)
          .add(RedisMetrics.unprocessedGauge)
          .addAll(SolrMetrics.allCollectors)
        metrics = metricsUpdaterTask(services)
        httpServer = httpServerTask(registryBuilder, services)
        tasks = services.messageHandlers.getAll + metrics + httpServer
        pm = services.backgroundManage
        _ <- tasks.toList.traverse_(pm.register.tupled)
        _ <- pm.startAll
        _ <- IO.never
      } yield ExitCode.Success
    }

  private def httpServerTask(
      registryBuilder: CollectorRegistryBuilder[IO],
      services: Services[IO]
  ): (TaskName, IO[Unit]) =
    val io = Routes[IO](registryBuilder, services)
      .flatMap(HttpServer.build(_, services.config.httpServerConfig))
      .use(_ => IO.never)
    TaskName.fromString("http server") -> io

  private def metricsUpdaterTask(services: Services[IO]): (TaskName, IO[Unit]) =
    val updateInterval = services.config.metricsUpdateInterval
    val io =
      if (updateInterval <= Duration.Zero)
        logger.info(
          s"Metric update interval is $updateInterval, disable periodic metric task"
        )
      else
        MetricsCollectorsUpdater[IO](
          services.config.clientId,
          services.config.queuesConfig,
          updateInterval,
          services.queueClient,
          services.solrClient
        ).run()
    TaskName.fromString("metrics updater") -> io

  private def runSolrMigrations(cfg: SearchProvisionConfig): IO[Unit] =
    SchemaMigrator[IO](cfg.solrConfig)
      .use(_.migrate(Migrations.all))
      .handleErrorWith { err =>
        logger.error("Running solr migrations failure, retrying", err) >>
          Temporal[IO].delayBy(runSolrMigrations(cfg), cfg.retryOnErrorDelay)
      }
