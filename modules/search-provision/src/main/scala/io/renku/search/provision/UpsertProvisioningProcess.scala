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

import cats.effect.{Async, Resource, Temporal}
import cats.syntax.all.*
import cats.{MonadThrow, Show}
import fs2.Chunk
import fs2.io.net.Network
import io.bullet.borer.Encoder
import io.github.arainko.ducktape.*
import io.renku.avro.codec.{AvroDecoder, AvroReader}
import io.renku.queue.client.{DataContentType, QueueClient, QueueMessage}
import io.renku.redis.client.{ClientId, QueueName, RedisConfig}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.solr.client.SolrConfig
import org.apache.avro.Schema
import scribe.Scribe

import scala.concurrent.duration.*

trait UpsertProvisioningProcess[F[_]]:
  def provisioningProcess: F[Unit]

object UpsertProvisioningProcess:
  private val clientId: ClientId = ClientId("search-provisioner")

  def make[F[_]: Async: Network: Scribe, In, Out](
      queueName: QueueName,
      inSchema: Schema,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  )(using
      Show[In],
      Transformer[In, Out],
      AvroDecoder[In],
      Encoder[Out]
  ): Resource[F, UpsertProvisioningProcess[F]] =
    SearchSolrClient.make[F](solrConfig).map {
      new UpsertProvisioningProcessImpl[F, In, Out](
        queueName,
        inSchema,
        clientId,
        QueueClient.make[F](redisConfig),
        _
      )
    }

private class UpsertProvisioningProcessImpl[F[_]: Async: Scribe, In, Out](
    queueName: QueueName,
    inSchema: Schema,
    clientId: ClientId,
    queueClientResource: Resource[F, QueueClient[F]],
    solrClient: SearchSolrClient[F]
)(using Show[In], Transformer[In, Out], AvroDecoder[In], Encoder[Out])
    extends UpsertProvisioningProcess[F]:

  override def provisioningProcess: F[Unit] =
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

  private lazy val logInfo: ((QueueMessage, Seq[In])) => F[Unit] = { case (m, v) =>
    Scribe[F].info(
      "Received message " +
        s"queue: $queueName, " +
        s"id: ${m.id}, " +
        s"source: ${m.header.source}, " +
        s"type: ${m.header.`type`} " +
        s"for: ${v.mkString_(", ")}"
    )
  }

  private val avro = AvroReader(inSchema)

  private def decodeMessage(queueClient: QueueClient[F])(
      message: QueueMessage
  ): F[(QueueMessage, Seq[In])] =
    MonadThrow[F]
      .fromEither(DataContentType.from(message.header.dataContentType))
      .flatMap { ct =>
        MonadThrow[F]
          .catchNonFatal {
            ct match {
              case DataContentType.Binary => avro.read[In](message.payload)
              case DataContentType.Json   => avro.readJson[In](message.payload)
            }
          }
          .map(message -> _)
          .onError(markProcessedOnFailure(message, queueClient))
      }

  private def pushToSolr(
      queueClient: QueueClient[F]
  )(chunk: Chunk[(QueueMessage, Seq[In])]): F[Unit] =
    chunk.toList match {
      case Nil => ().pure[F]
      case tuples =>
        val docs = toSolrDocuments(tuples.flatMap(_._2))
        val (lastMessage, _) = tuples.last
        solrClient
          .insert(docs)
          .flatMap(_ => markProcessed(lastMessage, queueClient))
          .onError(markProcessedOnFailure(lastMessage, queueClient))
    }

  private lazy val toSolrDocuments: Seq[In] => Seq[Out] =
    _.map(_.to[Out])

  private def markProcessedOnFailure(
      message: QueueMessage,
      queueClient: QueueClient[F]
  ): PartialFunction[Throwable, F[Unit]] = err =>
    markProcessed(message, queueClient) >>
      Scribe[F].error(s"Processing messageId: ${message.id} failed", err)

  private def markProcessed(message: QueueMessage, queueClient: QueueClient[F]): F[Unit] =
    queueClient.markProcessed(clientId, queueName, message.id)

  private def logAndRestart: Throwable => F[Unit] = err =>
    Scribe[F].error(s"Failure in the provisioning process for '$queueName'", err) >>
      Temporal[F].delayBy(provisioningProcess, 30 seconds)
