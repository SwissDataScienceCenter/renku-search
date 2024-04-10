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

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.*

import cats.effect.Async
import cats.effect.kernel.Ref
import cats.effect.std.Random
import cats.syntax.all.*
import fs2.{Chunk, Pipe, Stream}

import io.renku.queue.client.QueueMessage
import io.renku.search.provision.handler.Model$package.EntityOrPartial.given
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.EntityDocument
import io.renku.solr.client.ResponseHeader
import io.renku.solr.client.UpsertResponse

trait PushToSolr[F[_]]:
  def pushChunk: Pipe[F, Chunk[MessageReader.Message[EntityOrPartial]], UpsertResponse]
  def push1: Pipe[F, MessageReader.Message[EntityOrPartial], UpsertResponse]
  def push(
      onConflict: => Stream[F, UpsertResponse],
      maxTries: Ref[F, Int],
      maxWait: FiniteDuration = 100.millis
  ): Pipe[F, MessageReader.Message[EntityOrPartial], UpsertResponse]

object PushToSolr:

  def apply[F[_]: Async](
      solrClient: SearchSolrClient[F],
      reader: MessageReader[F]
  ): PushToSolr[F] =
    new PushToSolr[F] {
      val logger = scribe.cats.effect[F]
      def pushChunk
          : Pipe[F, Chunk[MessageReader.Message[EntityOrPartial]], UpsertResponse] =
        _.evalMap { docs =>
          val docSeq = docs.toList.flatMap(_.decoded)
          docs.last.map(_.raw) match
            case Some(lastMessage) =>
              logger.debug(s"Push ${docSeq} to solr") >>
                solrClient
                  .upsert(docSeq)
                  .onError(reader.markProcessedError(_, lastMessage.id)(using logger))
            case None =>
              val r = UpsertResponse.Success(ResponseHeader.empty)
              Async[F].pure(r)
        }

      def push1: Pipe[F, MessageReader.Message[EntityOrPartial], UpsertResponse] =
        _.map(Chunk.apply(_)).through(pushChunk)

      def push(
          onConflict: => Stream[F, UpsertResponse],
          max: Ref[F, Int],
          maxWait: FiniteDuration
      ): Pipe[F, MessageReader.Message[EntityOrPartial], UpsertResponse] =
        _.flatMap { msg =>
          Stream.emit(msg).through(push1).flatMap {
            case r @ UpsertResponse.Success(_) =>
              Stream.eval(reader.markProcessed(msg.id)).as(r)
            case r @ UpsertResponse.VersionConflict =>
              Stream.eval(max.updateAndGet(_ - 1)).flatMap { counter =>
                if (counter <= 0)
                  Stream
                    .eval(
                      logger
                        .warn(s"Retries on version conflict exceeded for message: $msg")
                    )
                    .evalMap(_ => reader.markProcessed(msg.id))
                    .as(r)
                else
                  Stream
                    .eval(Random.scalaUtilRandom[F])
                    .evalMap(_.betweenLong(10, maxWait.toMillis))
                    .map(FiniteDuration(_, TimeUnit.MILLISECONDS))
                    .evalTap(n =>
                      logger.debug(s"Version conflict updating solr, retry in $n")
                    )
                    .flatMap(Stream.sleep)
                    .evalMap(_ => onConflict.compile.lastOrError)
              }
          }
        }
    }
