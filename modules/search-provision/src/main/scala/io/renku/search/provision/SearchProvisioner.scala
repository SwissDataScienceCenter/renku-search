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
import cats.effect.{Async, Resource, Temporal}
import cats.syntax.all.*
import fs2.Chunk
import fs2.io.net.Network
import io.github.arainko.ducktape.*
import io.renku.avro.codec.AvroReader
import io.renku.avro.codec.decoders.all.given
import io.renku.events.v1
import io.renku.events.v1.{ProjectCreated, Visibility}
import io.renku.queue.client.*
import io.renku.redis.client.{ClientId, QueueName, RedisConfig}
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

  def make[F[_]: Async: Network](
      queueName: QueueName,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  ): Resource[F, SearchProvisioner[F]] =
    SearchSolrClient.make[F](solrConfig).map {
      new SearchProvisionerImpl[F](
        clientId,
        queueName,
        QueueClient.make[F](redisConfig),
        _
      )
    }

private class SearchProvisionerImpl[F[_]: Async](
    clientId: ClientId,
    queueName: QueueName,
    queueClientResource: Resource[F, QueueClient[F]],
    solrClient: SearchSolrClient[F]
) extends SearchProvisioner[F]:

  private given Scribe[F] = scribe.cats[F]

  override def provisionSolr: F[Unit] =
    queueClientResource
      .use { queueClient =>
        findLastProcessed(queueClient) >>= { maybeLastProcessed =>
          queueClient
            .acquireEventsStream(queueName, chunkSize = 1, maybeLastProcessed)
            .evalMap(decodeMessage(queueClient))
            .evalTap(logInfo)
            .groupWithin(chunkSize = 10, timeout = 500 millis)
            .evalMap(pushToSolr(queueClient))
            .compile
            .drain
            .handleErrorWith(logAndRestart)
        }
      }
      .handleErrorWith(logAndRestart)

  private def findLastProcessed(queueClient: QueueClient[F]) =
    queueClient.findLastProcessed(clientId, queueName)

  private lazy val logInfo: ((QueueMessage, Seq[ProjectCreated])) => F[Unit] = {
    case (m, v) =>
      Scribe[F].info(
        s"Received messageId: ${m.id} for projects: ${v.map(_.slug).mkString(", ")}"
      )
  }

  private val avro = AvroReader(ProjectCreated.SCHEMA$)

  private def decodeMessage(queueClient: QueueClient[F])(
      message: QueueMessage
  ): F[(QueueMessage, Seq[ProjectCreated])] =
    MonadThrow[F]
      .fromEither(DataContentType.from(message.header.dataContentType))
      .flatMap { ct =>
        MonadThrow[F]
          .catchNonFatal {
            ct match {
              case DataContentType.Binary => avro.read[ProjectCreated](message.payload)
              case DataContentType.Json => avro.readJson[ProjectCreated](message.payload)
            }
          }
          .map(message -> _)
          .onError(markProcessedOnFailure(message, queueClient))
      }

  private def pushToSolr(
      queueClient: QueueClient[F]
  )(chunk: Chunk[(QueueMessage, Seq[ProjectCreated])]): F[Unit] =
    chunk.toList match {
      case Nil => ().pure[F]
      case tuples =>
        val allSolrDocs = toSolrDocuments(tuples.flatMap(_._2))
        val (lastMessage, _) = tuples.last
        solrClient
          .insertProjects(allSolrDocs)
          .flatMap(_ => markProcessed(lastMessage, queueClient))
          .onError(markProcessedOnFailure(lastMessage, queueClient))
    }

  private given Transformer[v1.Visibility, projects.Visibility] =
    (from: v1.Visibility) => projects.Visibility.unsafeFromString(from.name())

  private lazy val toSolrDocuments: Seq[ProjectCreated] => Seq[Project] =
    _.map(_.to[Project])

  private def markProcessedOnFailure(
      message: QueueMessage,
      queueClient: QueueClient[F]
  ): PartialFunction[Throwable, F[Unit]] = err =>
    markProcessed(message, queueClient) >>
      Scribe[F].error(s"Processing messageId: ${message.id} failed", err)

  private def markProcessed(message: QueueMessage, queueClient: QueueClient[F]): F[Unit] =
    queueClient.markProcessed(clientId, queueName, message.id)

  private def logAndRestart: Throwable => F[Unit] = err =>
    Scribe[F].error("Failure in the provisioning process", err) >>
      Temporal[F].delayBy(provisionSolr, 30 seconds)
