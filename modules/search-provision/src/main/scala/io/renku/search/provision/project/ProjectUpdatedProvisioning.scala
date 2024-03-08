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
import io.bullet.borer.Codec.*
import io.bullet.borer.{Codec, Decoder, Encoder}
import io.github.arainko.ducktape.*
import io.renku.avro.codec.decoders.all.given
import io.renku.events.v1.ProjectUpdated
import io.renku.redis.client.{QueueName, RedisConfig}
import io.renku.search.provision.TypeTransformers.given
import io.renku.search.solr.documents
import io.renku.solr.client.SolrConfig
import scribe.Scribe

object ProjectUpdatedProvisioning:

  def make[F[_]: Async: Network](
      queueName: QueueName,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  ): Resource[F, UpdateProvisioningProcess[F]] =
    given Scribe[F] = scribe.cats[F]
    UpdateProvisioningProcess.make[F, ProjectUpdated, documents.Project](
      queueName,
      ProjectUpdated.SCHEMA$,
      idExtractor,
      docUpdate,
      redisConfig,
      solrConfig
    )

  private given Codec[documents.Project] = Codec[documents.Project](
    Encoder[documents.Entity].contramap(_.asInstanceOf[documents.Entity]),
    Decoder[documents.Entity].mapEither {
      case u: documents.Project => Right(u)
      case u                    => Left(s"${u.getClass} is not a Project document")
    }
  )

  private given Show[ProjectUpdated] =
    Show.show[ProjectUpdated](v => s"slug '${v.slug}'")

  private lazy val idExtractor: ProjectUpdated => String = _.id

  private lazy val docUpdate
      : ((ProjectUpdated, documents.Project)) => documents.Project = {
    case (update, orig) =>
      update
        .into[documents.Project]
        .transform(
          Field.const(_.createdBy, orig.createdBy),
          Field.const(_.creationDate, orig.creationDate),
          Field.default(_.score)
        )
  }
