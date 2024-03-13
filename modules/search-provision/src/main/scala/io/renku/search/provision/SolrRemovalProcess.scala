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
import cats.data.NonEmptyList
import cats.effect.{Async, Resource, Temporal}
import cats.syntax.all.*
import fs2.io.net.Network
import io.github.arainko.ducktape.*
import io.renku.avro.codec.AvroDecoder
import io.renku.queue.client.{QueueClient, QueueMessage, RequestId}
import io.renku.redis.client.{ClientId, QueueName, RedisConfig}
import io.renku.search.model.Id
import io.renku.search.solr.client.SearchSolrClient
import io.renku.solr.client.SolrConfig
import org.apache.avro.Schema
import scribe.Scribe

import scala.concurrent.duration.*

trait SolrRemovalProcess[F[_]]:
  def removalProcess: F[Unit]

object SolrRemovalProcess:

  def make[F[_]: Async: Network: Scribe, In](
      queueName: QueueName,
      inSchema: Schema,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig,
      onSolrPersist: Option[OnSolrPersist[F, In]]
  )(using
      Show[In],
      Transformer[In, Id],
      AvroDecoder[In]
  ): Resource[F, SolrRemovalProcess[F]] =
    SearchSolrClient.make[F](solrConfig).map {
      new SolrRemovalProcessImpl[F, In](
        queueName,
        ProvisioningProcess.clientId,
        QueueClient.make[F](redisConfig),
        _,
        QueueMessageDecoder[F, In](inSchema),
        onSolrPersist
      )
    }

private class SolrRemovalProcessImpl[F[_]: Async: Scribe, In](
    queueName: QueueName,
    clientId: ClientId,
    queueClientResource: Resource[F, QueueClient[F]],
    solrClient: SearchSolrClient[F],
    messageDecoder: QueueMessageDecoder[F, In],
    onSolrPersist: Option[OnSolrPersist[F, In]]
)(using Show[In], Transformer[In, Id], AvroDecoder[In])
    extends SolrRemovalProcess[F]:
  override def removalProcess: F[Unit] =
    queueClientResource
      .use { queueClient =>
        findLastProcessed(queueClient) >>= { maybeLastProcessed =>
          queueClient
            .acquireEventsStream(queueName, chunkSize = 1, maybeLastProcessed)
            .evalMap(decodeMessage(queueClient))
            .evalTap(logInfo)
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
      s"Received message queue: $queueName, " +
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

  private def deleteFromSolr(
      queueClient: QueueClient[F]
  ): ((QueueMessage, Seq[In])) => F[Unit] = { case (message, ins) =>
    toDocumentIds(ins).fold(().pure[F]) { ids =>
      (solrClient.deleteIds(ids) >> onPersist(queueClient, message, ins))
        .flatMap(_ => markProcessed(message, queueClient))
        .onError(markProcessedOnFailure(message, queueClient))
    }
  }

  private def onPersist(
      queueClient: QueueClient[F],
      message: QueueMessage,
      ins: Seq[In]
  ) =
    onSolrPersist.fold(().pure[F]) { p =>
      ins.toList.traverse_(
        p.execute(_, RequestId(message.header.requestId))(queueClient, solrClient)
      )
    }

  private lazy val toDocumentIds: Seq[In] => Option[NonEmptyList[Id]] =
    _.map(_.to[Id]).toList.toNel

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
