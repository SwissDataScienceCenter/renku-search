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

package io.renku.search.provision.project

import cats.Show
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.io.net.Network

import io.github.arainko.ducktape.*
import io.renku.avro.codec.decoders.all.given
import io.renku.events.v1
import io.renku.events.v1.{ProjectCreated, Visibility}
import io.renku.redis.client.{QueueName, RedisConfig}
import io.renku.search.model.*
import io.renku.search.provision.TypeTransformers.given
import io.renku.search.provision.UpsertProvisioningProcess
import io.renku.search.solr.documents
import io.renku.solr.client.SolrConfig
import scribe.Scribe

object ProjectCreatedProvisioning:

  def make[F[_]: Async: Network](
      queueName: QueueName,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  ): Resource[F, UpsertProvisioningProcess[F]] =
    given Scribe[F] = scribe.cats[F]
    UpsertProvisioningProcess.make[F, ProjectCreated, documents.Project](
      queueName,
      ProjectCreated.SCHEMA$,
      redisConfig,
      solrConfig
    )

  private given Show[ProjectCreated] =
    Show.show[ProjectCreated](pc => show"slug '${pc.slug}'")

  private given Transformer[ProjectCreated, documents.Project] =
    _.into[documents.Project].transform(
      Field.computed(_.owners, pc => List(Id(pc.createdBy))),
      Field.default(_.members),
      Field.default(_.score)
    )
