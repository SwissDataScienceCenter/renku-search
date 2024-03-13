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
import fs2.io.net.Network
import io.bullet.borer.Codec
import io.renku.avro.codec.AvroDecoder
import io.renku.queue.client.{QueueClient, QueueMessage}
import io.renku.redis.client.{ClientId, QueueName, RedisConfig}
import io.renku.search.model.Id
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.Entity
import io.renku.solr.client.SolrConfig
import scribe.Scribe

import scala.reflect.ClassTag

trait UpdateProvisioningProcess[F[_]] extends ProvisioningProcess[F]

object UpdateProvisioningProcess:

  def make[F[_]: Async: Network: Scribe, In, Out <: Entity](
      queueName: QueueName,
      idExtractor: In => Id,
      docUpdate: ((In, Out)) => Out,
      redisConfig: RedisConfig,
      solrConfig: SolrConfig
  )(using
      Show[In],
      AvroDecoder[In],
      Codec[Entity],
      QueueMessageDecoder[F, In],
      ClassTag[Out]
  ): Resource[F, UpdateProvisioningProcess[F]] =
    SearchSolrClient.make[F](solrConfig).map {
      new UpdateProvisioningProcessImpl[F, In, Out](
        queueName,
        ProvisioningProcess.clientId,
        idExtractor,
        docUpdate,
        QueueClient.make[F](redisConfig),
        _
      )
    }

private class UpdateProvisioningProcessImpl[F[_]: Async: Scribe, In, Out <: Entity](
    queueName: QueueName,
    clientId: ClientId,
    idExtractor: In => Id,
    docUpdate: ((In, Out)) => Out,
    queueClientResource: Resource[F, QueueClient[F]],
    solrClient: SearchSolrClient[F]
)(using Show[In], Codec[Entity], ClassTag[Out], QueueMessageDecoder[F, In])
    extends UpdateProvisioningProcess[F]:

  def process: F[Unit] = provisioningProcess

  override def provisioningProcess: F[Unit] =
    queueClientResource
      .use { queueClient =>
        findLastProcessed(queueClient) >>= { maybeLastProcessed =>
          queueClient
            .acquireEventsStream(queueName, chunkSize = 1, maybeLastProcessed)
            .evalMap(decodeMessage(queueClient))
            .evalTap(logInfo)
            .evalMap(fetchDocuments)
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
    QueueMessageDecoder[F, In]
      .decodeMessage(message)
      .tupleLeft(message)
      .onError(markProcessedOnFailure(message, queueClient))

  private lazy val fetchDocuments
      : ((QueueMessage, Seq[In])) => F[(QueueMessage, Seq[(In, Out)])] =
    case (m, ins) =>
      ins
        .map { in =>
          val docId = idExtractor(in)
          solrClient.findById[Out](docId) >>= {
            case Some(out) => (in, out).some.pure[F]
            case None =>
              Scribe[F]
                .warn(s"Document id: '$docId' for update doesn't exist in Solr; skipping")
                .as(Option.empty[Nothing])
          }
        }
        .sequence
        .map(_.flatten)
        .map((m, _))

  private def pushToSolr(
      queueClient: QueueClient[F]
  ): ((QueueMessage, Seq[(In, Out)])) => F[Unit] = { case (m, inOuts) =>
    inOuts match {
      case l if l.isEmpty => ().pure[F]
      case inOuts =>
        val updatedDocs = inOuts.map(docUpdate).map(_.widen)
        solrClient
          .insert(updatedDocs)
          .flatMap(_ => markProcessed(m, queueClient))
          .onError(markProcessedOnFailure(m, queueClient))
    }
  }

  private def markProcessedOnFailure(
      message: QueueMessage,
      queueClient: QueueClient[F]
  ): PartialFunction[Throwable, F[Unit]] = err =>
    markProcessed(message, queueClient) >>
      Scribe[F].error(s"Processing messageId: ${message.id} for '$queueName' failed", err)

  private def markProcessed(message: QueueMessage, queueClient: QueueClient[F]): F[Unit] =
    queueClient.markProcessed(clientId, queueName, message.id)
