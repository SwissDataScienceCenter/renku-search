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

package io.renku.search.provision.variant

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import fs2.{Chunk, Pipe}

import io.renku.queue.client.{QueueClient, QueueMessage}
import io.renku.redis.client.{ClientId, QueueName}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.Entity as Document

trait PushToSolr[F[_]]:
  def pushChunk: Pipe[F, Chunk[MessageReader.Message[Document]], Unit]
  def push: Pipe[F, MessageReader.Message[Document], Unit] =
    _.chunks.through(pushChunk)
  def push1: Pipe[F, MessageReader.Message[Document], Unit] =
    _.map(Chunk(_)).through(pushChunk)

object PushToSolr:

  def apply[F[_]: Sync](
      solrClient: SearchSolrClient[F],
      queueClient: Resource[F, QueueClient[F]],
      clientId: ClientId,
      queue: QueueName
  ): PushToSolr[F] =
    new PushToSolr[F] {
      val logger = scribe.cats.effect[F]
      def pushChunk: Pipe[F, Chunk[MessageReader.Message[Document]], Unit] =
        _.evalMap { docs =>
          val docSeq = docs.toList.flatMap(_.decoded)
          docs.last.map(_.raw) match
            case Some(lastMessage) =>
              solrClient
                .insert(docSeq)
                .flatMap(_ => queueClient.use(_.markProcessed(clientId, queue, lastMessage.id)))
                .onError(markProcessedOnFailure(lastMessage))
            case None =>
              Sync[F].unit
        }

      private def markProcessedOnFailure(
          message: QueueMessage
      ): PartialFunction[Throwable, F[Unit]] = err =>
        queueClient.use(_.markProcessed(clientId, queue, message.id)) >>
          logger.error(
            s"Processing messageId: ${message.id} for '${queue.name}' failed",
            err
          )
    }
