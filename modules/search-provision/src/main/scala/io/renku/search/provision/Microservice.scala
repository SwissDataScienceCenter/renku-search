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
import io.renku.redis.client.QueueName
import io.renku.search.provision.project.*
import io.renku.search.provision.user.*
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.migration.SchemaMigrator
import io.renku.search.solr.client.SearchSolrClient
import io.renku.queue.client.QueueClient

object Microservice extends IOApp:
  private val logger = scribe.cats.io
  private val loadConfig: IO[SearchProvisionConfig] =
    SearchProvisionConfig.config.load[IO]

  override def run(args: List[String]): IO[ExitCode] = prevRun(args)
  def prevRun(args: List[String]): IO[ExitCode] =
    for {
      config <- loadConfig
      _ <- IO(LoggingSetup.doConfigure(config.verbosity))
      _ <- runSolrMigrations(config)
      tasks = provisionProcesses(config)
      pm <- BackgroundProcessManage[IO](config.retryOnErrorDelay)
      _ <- tasks.traverse(e => e.task.map(_ -> e.queue)).use { ps =>
        ps.traverse_(e => pm.register(e._2.name, e._1.process)) >> pm.startAll
      }
    } yield ExitCode.Success

  def newRun(args: List[String]): IO[ExitCode] =
    loadConfig.flatMap { cfg =>
      resources(cfg).use { solrClient =>
        // The redis client must be initialized on each operation to
        // be able to connect to the cluster
        val queueClientResource = QueueClient.make[IO](cfg.redisConfig)
        val stepsForQueue = variant
          .PipelineSteps[IO](
            solrClient,
            queueClientResource,
            cfg.queuesConfig,
            1,
            ProvisioningProcess.clientId
          )
        val handlers = variant.MessageHandlers[IO](stepsForQueue, cfg.queuesConfig)
        for {
          pm <- BackgroundProcessManage[IO](cfg.retryOnErrorDelay)
          tasks = handlers.getAll.toList
          _ <- tasks.traverse_(pm.register.tupled) >> pm.startAll
        } yield ExitCode.Success
      }
    }

  def resources(cfg: SearchProvisionConfig) =
    SearchSolrClient.make[IO](cfg.solrConfig)

  final case class Provision(
      queue: QueueName,
      task: Resource[IO, BackgroundProcess[IO]]
  )

  def provisionProcesses(cfg: SearchProvisionConfig) = List(
    Provision(
      cfg.queuesConfig.projectCreated,
      ProjectCreatedProvisioning
        .make[IO](cfg.queuesConfig.projectCreated, cfg.redisConfig, cfg.solrConfig)
    ),
    Provision(
      cfg.queuesConfig.projectUpdated,
      ProjectUpdatedProvisioning
        .make[IO](cfg.queuesConfig.projectUpdated, cfg.redisConfig, cfg.solrConfig)
    ),
    Provision(
      cfg.queuesConfig.projectRemoved,
      ProjectRemovedProcess
        .make[IO](cfg.queuesConfig.projectRemoved, cfg.redisConfig, cfg.solrConfig)
    ),
    Provision(
      cfg.queuesConfig.projectAuthorizationAdded,
      AuthorizationAddedProvisioning
        .make[IO](
          cfg.queuesConfig.projectAuthorizationAdded,
          cfg.redisConfig,
          cfg.solrConfig
        )
    ),
    Provision(
      cfg.queuesConfig.projectAuthorizationUpdated,
      AuthorizationUpdatedProvisioning
        .make[IO](
          cfg.queuesConfig.projectAuthorizationUpdated,
          cfg.redisConfig,
          cfg.solrConfig
        )
    ),
    Provision(
      cfg.queuesConfig.projectAuthorizationRemoved,
      AuthorizationRemovedProvisioning
        .make[IO](
          cfg.queuesConfig.projectAuthorizationRemoved,
          cfg.redisConfig,
          cfg.solrConfig
        )
    ),
    Provision(
      cfg.queuesConfig.userAdded,
      UserAddedProvisioning
        .make[IO](cfg.queuesConfig.userAdded, cfg.redisConfig, cfg.solrConfig)
    ),
    Provision(
      cfg.queuesConfig.userUpdated,
      UserUpdatedProvisioning
        .make[IO](cfg.queuesConfig.userUpdated, cfg.redisConfig, cfg.solrConfig)
    ),
    Provision(
      cfg.queuesConfig.userRemoved,
      UserRemovedProcess
        .make[IO](
          cfg.queuesConfig.userRemoved,
          cfg.queuesConfig.projectAuthorizationRemoved,
          cfg.redisConfig,
          cfg.solrConfig
        )
    )
  )

  def runSolrMigrations(cfg: SearchProvisionConfig): IO[Unit] =
    SchemaMigrator[IO](cfg.solrConfig)
      .use(_.migrate(Migrations.all))
      .handleErrorWith { err =>
        logger.error("Running solr migrations failure, retrying", err) >>
          Temporal[IO].delayBy(runSolrMigrations(cfg), cfg.retryOnErrorDelay)
      }
