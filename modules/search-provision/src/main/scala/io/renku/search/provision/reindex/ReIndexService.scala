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

package io.renku.search.provision.reindex

import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream

import io.renku.queue.client.QueueClient
import io.renku.search.config.QueuesConfig
import io.renku.search.events.MessageId
import io.renku.search.provision.BackgroundProcessManage
import io.renku.search.provision.MessageHandlers.MessageHandlerKey
import io.renku.search.solr.client.SearchSolrClient

trait ReIndexService[F[_]]:
  def startReIndex(startMessage: Option[MessageId]): F[Boolean]

object ReIndexService:

  def apply[F[_]: Clock: Sync](
      bpm: BackgroundProcessManage[F],
      redisClient: Stream[F, QueueClient[F]],
      solrClient: SearchSolrClient[F],
      queueCfg: QueuesConfig
  ): ReIndexService[F] =
    new ReIndexService[F] {
      private val queueName = NonEmptyList.of(queueCfg.dataServiceAllEvents)
      private val logger = scribe.cats.effect[F]

      def startReIndex(startMessage: Option[MessageId]): F[Boolean] =
        for
          syncDoc <- ReIndexDocument.createNew[F](startMessage)
          upsertResp <- solrClient.upsert(Seq(syncDoc))
          _ <- logger.debug(s"Insert reindex sync document: $upsertResp")
          res <-
            if (upsertResp.isFailure)
              logger.debug(s"Re-Index called while already in progress").as(false)
            else dropIndexAndRestart(syncDoc, startMessage)
        yield res

      private def dropIndexAndRestart(
          syncDoc: ReIndexDocument,
          startMessage: Option[MessageId]
      ) =
        for
          _ <- logger.info(
            s"Starting re-indexing all data, since message ${syncDoc.messageId}"
          )
          _ <- bpm.cancelProcesses(MessageHandlerKey.isInstance)
          _ <- logger.info("Background processes stopped")
          _ <- startMessage match
            case Some(msgId) =>
              logger.info("Set last seen message id to $msgId for $queueName") >>
                redisClient
                  .evalMap(_.markProcessed(queueName, msgId))
                  .take(1)
                  .compile
                  .drain
            case None =>
              // a special case until the other queues are removed and
              // only `data_service.all_events` is left. we remove the
              // last seen message-ids from all queue names, in
              // contrast for setting a message-id which can only be
              // done for one queue (and doesn't make sense to
              // implement for all)
              logger.info(s"Remove last processed message id for ${queueCfg.all}") >>
                redisClient
                  .evalMap(rc =>
                    queueCfg.all.toList.traverse_(q =>
                      rc.removeLastProcessed(NonEmptyList.of(q))
                    )
                  )
                  .take(1)
                  .compile
                  .drain
          _ <- logger.info("Delete SOLR index")
          _ <- solrClient.deletePublicData
          _ <- logger.info("Start background processes")
          _ <- bpm.background(MessageHandlerKey.isInstance)
          _ <- solrClient.deleteIds(NonEmptyList.of(syncDoc.id))
        yield true
    }
