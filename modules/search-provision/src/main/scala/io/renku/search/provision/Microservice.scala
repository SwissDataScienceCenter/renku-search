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

import io.renku.search.http.HttpServer
import io.renku.search.logging.LoggingSetup
import io.renku.search.metrics.CollectorRegistryBuilder
import io.renku.search.provision.BackgroundProcessManage.TaskName
import io.renku.search.provision.metrics.*
import io.renku.search.sentry.Level
import io.renku.search.sentry.SentryEvent
import io.renku.search.sentry.scribe.SentryHandler
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.migration.{MigrateResult, SchemaMigrator}

object Microservice extends IOApp:

  private val logger = scribe.cats.io

  override def run(args: List[String]): IO[ExitCode] =
    Services.make[IO].use { services =>
      for {
        startupEvent <- makeStartupEvent(services.config)
        _ <- services.sentry.capture(startupEvent)
        _ <- IO(
          LoggingSetup.doConfigure(
            services.config.verbosity,
            Some(SentryHandler(services.sentry)(using runtime))
          )
        )
        migrateResult <- runSolrMigrations(services.config)
        _ <- IO.whenA(migrateResult.migrationsSkipped > 0)(
          logger
            .warn(s"There were ${migrateResult.migrationsSkipped} skipped migrations!")
        )
        // this is really only safe for a single provisioning service,
        // but for the immediate future that is the situation. So do
        // this to recover from crashes during a lock is held
        _ <- services.resetLockDocuments.handleErrorWith { ex =>
          logger.warn(s"Resetting locks on start failed", ex)
        }
        registryBuilder = CollectorRegistryBuilder[IO].withJVMMetrics
          .add(RedisMetrics.queueSizeGauge)
          .add(RedisMetrics.unprocessedGauge)
          .addAll(MessageMetrics.all)
          .addAll(SolrMetrics.allCollectors)
        metrics = metricsUpdaterTask(services)
        httpServer = httpServerTask(registryBuilder, services)
        tasks = services.messageHandlers.getAll + metrics + httpServer
        pm = services.backgroundManage
        _ <- tasks.toList.traverse_(pm.register.tupled)
        _ <-
          if (migrateResult.reindexRequired)
            logger.info(
              "Re-Index is required after migrations have applied!"
            ) >> services.reprovision.recreateIndex
          else IO.unit
        _ <- pm.startAll
        _ <- IO.never
      } yield ExitCode.Success
    }

  private def httpServerTask(
      registryBuilder: CollectorRegistryBuilder[IO],
      services: Services[IO]
  ): (TaskName, IO[Unit]) =
    val io = Routes[IO](registryBuilder, services)
      .flatMap(routes =>
        HttpServer[IO](services.config.httpServerConfig)
          .withDefaultErrorHandler(logger)
          .withDefaultLogging(logger)
          .withHttpRoutes(routes)
          .build
      )
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

  private def runSolrMigrations(cfg: SearchProvisionConfig): IO[MigrateResult] =
    SchemaMigrator[IO](cfg.solrConfig)
      .use(_.migrate(Migrations.all))
      .handleErrorWith { err =>
        logger.error("Running solr migrations failure, retrying", err) >>
          Temporal[IO].delayBy(runSolrMigrations(cfg), cfg.retryOnErrorDelay)
      }

  private def makeStartupEvent(config: SearchProvisionConfig): IO[SentryEvent] =
    SentryEvent.create[IO](Level.Info, s"Search Provisioner starting up with $config")
