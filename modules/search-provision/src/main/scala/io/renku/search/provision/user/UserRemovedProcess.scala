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

package io.renku.search.provision.user

import cats.Show
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.io.net.Network
import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.github.arainko.ducktape.*
import io.renku.avro.codec.decoders.all.given
import io.renku.avro.codec.encoders.all.given
import io.renku.events.v1
import io.renku.events.v1.{ProjectAuthorizationRemoved, UserRemoved}
import io.renku.queue.client.*
import io.renku.queue.client.DataContentType.Binary
import io.renku.redis.client.{QueueName, RedisConfig}
import io.renku.search.model.Id
import io.renku.search.provision.ProvisioningProcess.clientId
import io.renku.search.provision.{OnSolrPersist, SolrRemovalProcess}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.Project
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.{QueryData, SolrConfig}
import scribe.Scribe

object UserRemovedProcess:

  def make[F[_]: Async: Network](
      userRemovedQueue: QueueName,
      authRemovedQueue: QueueName,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  ): Resource[F, SolrRemovalProcess[F]] =
    given Scribe[F] = scribe.cats[F]

    SolrRemovalProcess.make[F, UserRemoved](
      userRemovedQueue,
      UserRemoved.SCHEMA$,
      redisConfig,
      solrConfig,
      onSolrPersist = Some(onSolrPersist[F](authRemovedQueue))
    )

  private given Show[UserRemoved] =
    Show.show[UserRemoved](e => show"id '${e.id}'")

  private given Transformer[UserRemoved, Id] =
    r => Id(r.id)

  private def onSolrPersist[F[_]: Async](authRemovedQueue: QueueName) =

    case class ProjectId(id: String)
    given Decoder[ProjectId] = deriveDecoder[ProjectId]

    new OnSolrPersist[F, UserRemoved] {
      override def execute(in: UserRemoved, requestId: RequestId)(
          queueClient: QueueClient[F],
          solrClient: SearchSolrClient[F]
      ): F[Unit] =
        findAffectedProjects(solrClient, in.id)
          .evalMap(enqueueAuthRemoved(queueClient, requestId, _, in.id))
          .compile
          .drain

      private def findAffectedProjects(
          sc: SearchSolrClient[F],
          userId: String
      ): Stream[F, ProjectId] =
        Stream
          .iterate(1)(_ + 1)
          .evalMap(p => sc.query[ProjectId](prepareQuery(userId, p)))
          .map(_.responseBody.docs)
          .takeWhile(_.nonEmpty)
          .flatMap(Stream.emits)

      private val pageSize = 20

      private def prepareQuery(userId: String, page: Int) =
        QueryData(
          s"${Fields.entityType}:${Project.entityType} ${Fields.owners}:$userId ${Fields.members}:$userId",
          filter = Seq.empty,
          limit = pageSize * page,
          offset = pageSize * (page - 1)
        ).withFields(Fields.id)

      private def enqueueAuthRemoved(
          qc: QueueClient[F],
          requestId: RequestId,
          projectId: ProjectId,
          userId: String
      ): F[Unit] =
        qc.enqueue(
          authRemovedQueue,
          createHeader(requestId),
          ProjectAuthorizationRemoved(projectId.id, userId)
        ).void

      private def createHeader(requestId: RequestId) =
        MessageHeader(
          MessageSource(clientId.value),
          ProjectAuthorizationRemoved.SCHEMA$,
          Binary,
          SchemaVersion("V1"),
          CreationTime.now,
          requestId
        )
    }
