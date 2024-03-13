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
package project

import cats.Show
import cats.effect.{Async, Resource}
import fs2.io.net.Network
import io.renku.avro.codec.decoders.all.given
import io.renku.events.v1.ProjectAuthorizationUpdated
import io.renku.redis.client.{QueueName, RedisConfig}
import io.renku.search.model.Id
import io.renku.search.provision.TypeTransformers.given
import io.renku.search.solr.documents
import io.renku.solr.client.SolrConfig
import scribe.Scribe

object AuthorizationUpdatedProvisioning:

  def make[F[_]: Async: Network](
      queueName: QueueName,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  ): Resource[F, UpdateProvisioningProcess[F]] =
    given Scribe[F] = scribe.cats[F]

    UpdateProvisioningProcess.make[F, ProjectAuthorizationUpdated, documents.Project](
      queueName,
      ProjectAuthorizationUpdated.SCHEMA$,
      idExtractor,
      docUpdate,
      redisConfig,
      solrConfig
    )

  private given Show[ProjectAuthorizationUpdated] =
    Show.show[ProjectAuthorizationUpdated](v =>
      s"projectId '${v.projectId}', userId '${v.userId}', role '${v.role}'"
    )

  private lazy val idExtractor: ProjectAuthorizationUpdated => Id =
    pau => Id(pau.projectId)

  private lazy val docUpdate
      : ((ProjectAuthorizationUpdated, documents.Project)) => documents.Project = {
    case (update, orig) =>
      orig.addMember(
        Id(update.userId),
        memberRoleTransformer.transform(update.role)
      )
  }
