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
import cats.Show
import fs2.Chunk
import fs2.io.net.Network
import io.github.arainko.ducktape.*
import io.renku.avro.codec.{AvroDecoder, AvroReader}
import io.renku.queue.client.{QueueClient, QueueMessage}
import io.renku.redis.client.{ClientId, QueueName, RedisConfig}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.solr.client.SolrConfig
import org.apache.avro.Schema
import scribe.Scribe
import io.renku.search.solr.documents.DocumentId
import scala.concurrent.duration.*
import cats.data.NonEmptyList

trait SolrRemovalProcess[F[_]]:
  def removalProcess: F[Unit]

object SolrRemovalProcess:
  private val clientId: ClientId = ClientId("search-provisioner")

  def make[F[_]: Async: Network: Scribe, In](
      queueName: QueueName,
      inSchema: Schema,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  )(using
      Show[In],
      Transformer[In, DocumentId],
      AvroDecoder[In]
  ): Resource[F, SolrRemovalProcess[F]] =
    SearchSolrClient.make[F](solrConfig).map {
      new SolrRemovalProcessImpl[F, In](
        queueName,
        inSchema,
        clientId,
        QueueClient.make[F](redisConfig),
        _,
        QueueMessageDecoder[F, In](inSchema)
      )
    }

private class SolrRemovalProcessImpl[F[_]: Async: Scribe, In](
    queueName: QueueName,
    inSchema: Schema,
    clientId: ClientId,
    queueClientResource: Resource[F, QueueClient[F]],
    solrClient: SearchSolrClient[F],
    messageDecoder: QueueMessageDecoder[F, In]
)(using Show[In], Transformer[In, DocumentId], AvroDecoder[In])
    extends SolrRemovalProcess[F]:
  override def removalProcess: F[Unit] =
    queueClientResource
      .use { queueClient =>
        findLastProcessed(queueClient) >>= { maybeLastProcessed =>
          queueClient
            .acquireEventsStream(queueName, chunkSize = 1, maybeLastProcessed)
            .evalMap(decodeMessage(queueClient))
            .evalTap(logInfo)
            .groupWithin(chunkSize = 10, timeout = 500 millis)
            .evalMap(deleteFromSolr(queueClient))
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
      "Received mesage " + s"queue: $queueName, " +
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
    messageDecoder
      .decodeMessage(message)
      .tupleLeft(message)
      .onError(markProcessedOnFailure(message, queueClient))

  private def deleteFromSolr(
      queueClient: QueueClient[F]
  )(chunk: Chunk[(QueueMessage, Seq[In])]): F[Unit] =
    chunk.toList match {
      case Nil => ().pure[F]
      case tuples =>
        val ids = toDocumentIds(tuples.flatMap(_._2))
        ids match {
          case Some(ids) =>
            val (lastMessage, _) = tuples.last
            solrClient
              .deleteIds(ids)
              .flatMap(_ => markProcessed(lastMessage, queueClient))
              .onError(markProcessedOnFailure(lastMessage, queueClient))
          case None => ().pure[F]
        }
    }

  private lazy val toDocumentIds: Seq[In] => Option[NonEmptyList[DocumentId]] =
    _.map(_.to[DocumentId]).toList.toNel

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
      Temporal[F].delayBy(removalProcess, 30 seconds)
