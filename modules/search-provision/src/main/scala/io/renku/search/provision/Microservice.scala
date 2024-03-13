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
import scribe.Scribe
import scribe.cats.*

object Microservice extends IOApp:

  private val loadConfig: IO[SearchProvisionConfig] =
    SearchProvisionConfig.config.load[IO]

  override def run(args: List[String]): IO[ExitCode] =
    for {
      config <- loadConfig
      _ <- IO(LoggingSetup.doConfigure(config.verbosity))
      _ <- runSolrMigrations(config)
      _ <- startProvisioners(config)
    } yield ExitCode.Success

  private def startProvisioners(cfg: SearchProvisionConfig): IO[Unit] =
    List(
      (
        "ProjectCreated",
        cfg.queuesConfig.projectCreated,
        ProjectCreatedProvisioning
          .make[IO](cfg.queuesConfig.projectCreated, cfg.redisConfig, cfg.solrConfig)
          .map(_.provisioningProcess.start)
      ),
      (
        "ProjectUpdated",
        cfg.queuesConfig.projectUpdated,
        ProjectUpdatedProvisioning
          .make[IO](cfg.queuesConfig.projectUpdated, cfg.redisConfig, cfg.solrConfig)
          .map(_.provisioningProcess.start)
      ),
      (
        "ProjectRemoved",
        cfg.queuesConfig.projectRemoved,
        ProjectRemovedProcess
          .make[IO](cfg.queuesConfig.projectRemoved, cfg.redisConfig, cfg.solrConfig)
          .map(_.removalProcess.start)
      ),
      (
        "ProjectAuthorizationAdded",
        cfg.queuesConfig.projectAuthorizationAdded,
        AuthorizationAddedProvisioning
          .make[IO](
            cfg.queuesConfig.projectAuthorizationAdded,
            cfg.redisConfig,
            cfg.solrConfig
          )
          .map(_.provisioningProcess.start)
      ),
      (
        "ProjectAuthorizationUpdated",
        cfg.queuesConfig.projectAuthorizationUpdated,
        AuthorizationUpdatedProvisioning
          .make[IO](
            cfg.queuesConfig.projectAuthorizationUpdated,
            cfg.redisConfig,
            cfg.solrConfig
          )
          .map(_.provisioningProcess.start)
      ),
      (
        "ProjectAuthorizationRemoved",
        cfg.queuesConfig.projectAuthorizationRemoved,
        AuthorizationRemovedProvisioning
          .make[IO](
            cfg.queuesConfig.projectAuthorizationRemoved,
            cfg.redisConfig,
            cfg.solrConfig
          )
          .map(_.provisioningProcess.start)
      ),
      (
        "UserAdded",
        cfg.queuesConfig.userAdded,
        UserAddedProvisioning
          .make[IO](cfg.queuesConfig.userAdded, cfg.redisConfig, cfg.solrConfig)
          .map(_.provisioningProcess.start)
      ),
      (
        "UserUpdated",
        cfg.queuesConfig.userUpdated,
        UserUpdatedProvisioning
          .make[IO](cfg.queuesConfig.userUpdated, cfg.redisConfig, cfg.solrConfig)
          .map(_.provisioningProcess.start)
      ),
      (
        "UserRemoved",
        cfg.queuesConfig.userRemoved,
        UserRemovedProcess
          .make[IO](
            cfg.queuesConfig.userRemoved,
            cfg.queuesConfig.projectAuthorizationRemoved,
            cfg.redisConfig,
            cfg.solrConfig
          )
          .map(_.removalProcess.start)
      )
    ).parTraverse_(startProcess(cfg))
      .flatMap(_ => IO.never)

  private def startProcess(
      cfg: SearchProvisionConfig
  ): ((String, QueueName, Resource[IO, IO[FiberIO[Unit]]])) => IO[Unit] = {
    case t @ (name, queue, resource) =>
      resource
        .use(_ =>
          Scribe[IO].info(s"'$name' provisioning process started on '$queue' queue")
        )
        .handleErrorWith { err =>
          Scribe[IO].error(
            s"Starting provisioning process for '$name' failed, retrying",
            err
          ) >> Temporal[IO].delayBy(startProcess(cfg)(t), cfg.retryOnErrorDelay)
        }
  }

  private def runSolrMigrations(cfg: SearchProvisionConfig): IO[Unit] =
    SchemaMigrator[IO](cfg.solrConfig)
      .use(_.migrate(Migrations.all))
      .handleErrorWith { err =>
        Scribe[IO].error("Running solr migrations failure, retrying", err) >>
          Temporal[IO].delayBy(runSolrMigrations(cfg), cfg.retryOnErrorDelay)
      }
