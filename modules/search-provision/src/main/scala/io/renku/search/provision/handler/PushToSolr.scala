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

package io.renku.search.provision.handler

import cats.effect.Async
import cats.syntax.all.*
import fs2.{Chunk, Pipe}

import io.renku.search.events.EventMessage
import io.renku.search.provision.handler.Model$package.EntityOrPartial.given
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.EntityDocument
import io.renku.solr.client.ResponseHeader
import io.renku.solr.client.UpsertResponse

trait PushToSolr[F[_]]:
  def pushAll(msg: EventMessage[EntityOrPartial]): F[UpsertResponse]
  def push1(e: EntityOrPartial): F[UpsertResponse]

object PushToSolr:

  def apply[F[_]: Async](
      solrClient: SearchSolrClient[F],
      reader: MessageReader[F]
  ): PushToSolr[F] =
    new PushToSolr[F] {
      val logger = scribe.cats.effect[F]

      def pushAll(msg: EventMessage[EntityOrPartial]): F[UpsertResponse] =
        val docs = msg.payload
        if (docs.isEmpty)
          logger
            .debug(s"Attempt to push a message with empty payload: ${msg.header}")
            .as(UpsertResponse.Success(ResponseHeader.empty))
        else solrClient.upsert(docs)

      private def pushChunk
          : Pipe[F, Chunk[EventMessage[EntityOrPartial]], UpsertResponse] =
        _.evalMap { docs =>
          val docSeq = docs.toList.flatMap(_.payload)
          docs.last match
            case Some(lastMessage) =>
              logger.debug(s"Push ${docSeq} to solr") >>
                solrClient
                  .upsert(docSeq)
                  .onError(
                    reader.markProcessedError(_, lastMessage.id)(using logger)
                  )
            case None =>
              val r = UpsertResponse.Success(ResponseHeader.empty)
              Async[F].pure(r)
        }

      def push1(e: EntityOrPartial): F[UpsertResponse] =
        solrClient.upsert(Seq(e))
    }
