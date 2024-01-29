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
import io.renku.queue.client.QueueName
import io.renku.redis.client.RedisUrl
import io.renku.search.solr.schema.Migrations
import io.renku.solr.client.SolrConfig
import io.renku.solr.client.migration.SchemaMigrator
import org.http4s.Uri
import org.http4s.implicits.*
import scribe.Scribe
import scribe.cats.*

import scala.concurrent.duration.*

object Microservice extends IOApp:

  private val queueName = QueueName("events")
  private val redisUrl = RedisUrl("redis://localhost:6379")
  private val solrConfig = SolrConfig(
    baseUrl = uri"http://localhost:8983" / "solr",
    core = "search-core-test",
    commitWithin = Some(Duration.Zero),
    logMessageBodies = true
  )
  private val retryOnErrorDelay = 2 seconds

  override def run(args: List[String]): IO[ExitCode] =
    (runSolrMigrations >> startProvisioning)
      .as(ExitCode.Success)

  private def startProvisioning: IO[Unit] =
    SearchProvisioner[IO](queueName, redisUrl, solrConfig)
      .use(_.provisionSolr)
      .handleErrorWith { err =>
        Scribe[IO].error("Starting provisioning failure, retrying", err) >>
          Temporal[IO].delayBy(startProvisioning, retryOnErrorDelay)
      }

  private def runSolrMigrations: IO[Unit] =
    SchemaMigrator[IO](solrConfig)
      .use(_.migrate(Migrations.all))
      .handleErrorWith { err =>
        Scribe[IO].error("Running solr migrations failure, retrying", err) >>
          Temporal[IO].delayBy(runSolrMigrations, retryOnErrorDelay)
      }
