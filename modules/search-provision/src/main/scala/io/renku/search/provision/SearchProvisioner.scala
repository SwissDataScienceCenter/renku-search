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
import fs2.Chunk
import fs2.io.net.Network
import io.renku.avro.codec.AvroReader
import io.renku.avro.codec.decoders.all.given
import io.renku.events.v1.ProjectCreated
import io.renku.queue.client.*
import io.renku.redis.client.RedisConfig
import io.renku.search.model.*
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.*
import io.renku.solr.client.SolrConfig
import scribe.Scribe

import scala.concurrent.duration.*

trait SearchProvisioner[F[_]]:
  def provisionSolr: F[Unit]

object SearchProvisioner:

  private val clientId: ClientId = ClientId("search-provisioner")

  def apply[F[_]: Async: Network](
      queueName: QueueName,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  ): Resource[F, SearchProvisioner[F]] =
    QueueClient[F](redisConfig)
      .flatMap(qc => SearchSolrClient[F](solrConfig).tupleLeft(qc))
      .map { case (qc, sc) => new SearchProvisionerImpl[F](clientId, queueName, qc, sc) }

private class SearchProvisionerImpl[F[_]: Async](
    clientId: ClientId,
    queueName: QueueName,
    queueClient: QueueClient[F],
    solrClient: SearchSolrClient[F]
) extends SearchProvisioner[F]:

  private given Scribe[F] = scribe.cats[F]

  override def provisionSolr: F[Unit] =
    findLastProcessed >>= { maybeLastProcessed =>
      queueClient
        .acquireEventsStream(queueName, chunkSize = 1, maybeLastProcessed)
        .evalMap(decodeMessage)
        .evalTap { case (m, v) => Scribe[F].info(s"Received messageId: ${m.id} $v") }
        .groupWithin(chunkSize = 10, timeout = 500 millis)
        .evalMap(pushToSolr)
        .compile
        .drain
        .handleErrorWith(logAndRestart)
    }

  private def findLastProcessed =
    queueClient.findLastProcessed(clientId, queueName)

  private val avro = AvroReader(ProjectCreated.SCHEMA$)

  private def decodeMessage(message: Message): F[(Message, Seq[ProjectCreated])] =
    MonadThrow[F]
      .catchNonFatal {
        message.encoding match {
          case Encoding.Binary => avro.read[ProjectCreated](message.payload)
          case Encoding.Json   => avro.readJson[ProjectCreated](message.payload)
        }
      }
      .map(message -> _)
      .onError(markProcessedOnFailure(message))

  private def pushToSolr(chunk: Chunk[(Message, Seq[ProjectCreated])]): F[Unit] =
    chunk.toList match {
      case Nil => ().pure[F]
      case tuples =>
        val allSolrDocs = toSolrDocuments(tuples.flatMap(_._2))
        val (lastMessage, _) = tuples.last
        solrClient
          .insertProjects(allSolrDocs)
          .flatMap(_ => markProcessed(lastMessage))
          .onError(markProcessedOnFailure(lastMessage))
    }

  private lazy val toSolrDocuments: Seq[ProjectCreated] => Seq[Project] =
    _.map { pc =>

      def toUser(id: String): User = User(users.Id(id))

      Project(
        projects.Id(pc.id),
        projects.Name(pc.name),
        projects.Slug(pc.slug),
        pc.repositories.map(projects.Repository(_)),
        projects.Visibility.unsafeFromString(pc.visibility.name()),
        pc.description.map(projects.Description(_)),
        toUser(pc.createdBy),
        projects.CreationDate(pc.creationDate),
        pc.members.map(toUser)
      )
    }

  private def markProcessedOnFailure(
      message: Message
  ): PartialFunction[Throwable, F[Unit]] = err =>
    markProcessed(message) >>
      Scribe[F].error(s"Processing messageId: ${message.id} failed", err)

  private def markProcessed(message: Message): F[Unit] =
    queueClient.markProcessed(clientId, queueName, message.id)

  private def logAndRestart: Throwable => F[Unit] = err =>
    Scribe[F].error("Failure in the provisioning process", err) >>
      provisionSolr
