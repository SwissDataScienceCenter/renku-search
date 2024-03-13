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

import cats.Show
import cats.effect.*
import cats.syntax.all.*
import fs2.Chunk
import fs2.io.net.Network
import io.bullet.borer.Encoder
import io.github.arainko.ducktape.*
import io.renku.avro.codec.AvroDecoder
import io.renku.queue.client.{QueueClient, QueueMessage}
import io.renku.redis.client.{ClientId, QueueName, RedisConfig}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.Entity
import io.renku.solr.client.SolrConfig
import org.apache.avro.Schema
import scribe.Scribe

import scala.concurrent.duration.*

trait UpsertProvisioningProcess[F[_]] extends ProvisioningProcess[F]

object UpsertProvisioningProcess:

  def make[F[_]: Async: Network: Scribe, In, Out <: Entity](
      queueName: QueueName,
      inSchema: Schema,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  )(using
      Show[In],
      Transformer[In, Out],
      AvroDecoder[In],
      Encoder[Entity]
  ): Resource[F, UpsertProvisioningProcess[F]] =
    SearchSolrClient.make[F](solrConfig).map {
      new UpsertProvisioningProcessImpl[F, In, Out](
        queueName,
        ProvisioningProcess.clientId,
        QueueClient.make[F](redisConfig),
        _,
        QueueMessageDecoder[F, In](inSchema)
      )
    }

private class UpsertProvisioningProcessImpl[F[_]: Async: Scribe, In, Out <: Entity](
    queueName: QueueName,
    clientId: ClientId,
    queueClientResource: Resource[F, QueueClient[F]],
    solrClient: SearchSolrClient[F],
    messageDecoder: QueueMessageDecoder[F, In]
)(using Show[In], Transformer[In, Out], AvroDecoder[In], Encoder[Entity])
    extends UpsertProvisioningProcess[F]:

  def process: F[Unit] = provisioningProcess

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
        }
      }

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

  private def decodeMessage(queueClient: QueueClient[F])(
      message: QueueMessage
  ): F[(QueueMessage, Seq[In])] =
    messageDecoder
      .decodeMessage(message)
      .tupleLeft(message)
      .onError(markProcessedOnFailure(message, queueClient))

  private def pushToSolr(
      queueClient: QueueClient[F]
  )(chunk: Chunk[(QueueMessage, Seq[In])]): F[Unit] =
    chunk.toList match {
      case Nil => ().pure[F]
      case tuples =>
        val docs = toSolrDocuments(tuples.flatMap(_._2))
        val (lastMessage, _) = tuples.last
        solrClient
          .insert(docs.map(_.widen))
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
      Scribe[F].error(s"Processing messageId: ${message.id} for '$queueName' failed", err)

  private def markProcessed(message: QueueMessage, queueClient: QueueClient[F]): F[Unit] =
    queueClient.markProcessed(clientId, queueName, message.id)
