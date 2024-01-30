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

import cats.MonadThrow
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.io.net.Network
import io.renku.avro.codec.AvroReader
import io.renku.avro.codec.decoders.all.given
import io.renku.messages.ProjectCreated
import io.renku.queue.client.{Message, QueueClient, QueueName}
import io.renku.redis.client.RedisUrl
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.Project
import io.renku.solr.client.SolrConfig
import org.apache.avro.AvroRuntimeException
import scribe.Scribe

trait SearchProvisioner[F[_]]:
  def provisionSolr: F[Unit]

object SearchProvisioner:
  def apply[F[_]: Async: Network](
      queueName: QueueName,
      redisUrl: RedisUrl,
      solrConfig: SolrConfig
  ): Resource[F, SearchProvisioner[F]] =
    QueueClient[F](redisUrl)
      .flatMap(qc => SearchSolrClient[F](solrConfig).tupleLeft(qc))
      .map { case (qc, sc) => new SearchProvisionerImpl[F](queueName, qc, sc) }

private class SearchProvisionerImpl[F[_]: Async](
    queueName: QueueName,
    queueClient: QueueClient[F],
    solrClient: SearchSolrClient[F]
) extends SearchProvisioner[F]:

  private given Scribe[F] = scribe.cats[F]

  override def provisionSolr: F[Unit] =
    queueClient
      .acquireEventsStream(queueName, chunkSize = 1, maybeOffset = None)
      .evalMap(decodeEvent)
      .evalTap(decoded => Scribe[F].info(s"Received $decoded"))
      .flatMap(decoded => Stream.emits[F, ProjectCreated](decoded))
      .evalMap(pushToSolr)
      .compile
      .drain

  private val avro = AvroReader(ProjectCreated.SCHEMA$)

  private def decodeEvent(message: Message): F[Seq[ProjectCreated]] =
    decodeBinary(message).orElse(decodeJson(message))

  private def decodeBinary(message: Message): F[Seq[ProjectCreated]] =
    MonadThrow[F].catchOnly[AvroRuntimeException](
      avro.read[ProjectCreated](message.payload)
    )

  private def decodeJson(message: Message): F[Seq[ProjectCreated]] =
    MonadThrow[F].catchOnly[AvroRuntimeException](
      avro.readJson[ProjectCreated](message.payload)
    )

  private def pushToSolr(pc: ProjectCreated): F[Unit] =
    solrClient
      .insertProject(
        Project(id = pc.id, name = pc.name, description = pc.description)
      )
