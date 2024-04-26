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

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.all.*
import fs2.{Chunk, Pipe, Stream}
import io.renku.search.provision.handler.DeleteFromSolr.DeleteResult
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.events.EventMessage

trait DeleteFromSolr[F[_]]:
  def tryDeleteAll[A](using IdExtractor[A]): Pipe[F, EventMessage[A], DeleteResult[A]]
  def deleteAll[A](using IdExtractor[A]): Pipe[F, Chunk[EventMessage[A]], Unit]
  def whenSuccess[A](fb: EventMessage[A] => F[Unit]): Pipe[F, DeleteResult[A], Unit]

object DeleteFromSolr:
  enum DeleteResult[A](val message: EventMessage[A]):
    case Success(override val message: EventMessage[A]) extends DeleteResult(message)
    case Failed(override val message: EventMessage[A], error: Throwable)
        extends DeleteResult(message)
    case NoIds(override val message: EventMessage[A]) extends DeleteResult(message)

  object DeleteResult:
    def from[A](msg: EventMessage[A])(eab: Either[Throwable, Unit]): DeleteResult[A] =
      eab.fold(err => DeleteResult.Failed(msg, err), _ => DeleteResult.Success(msg))

  def apply[F[_]: Sync](
      solrClient: SearchSolrClient[F],
      reader: MessageReader[F]
  ): DeleteFromSolr[F] =
    new DeleteFromSolr[F] {
      val logger = scribe.cats.effect[F]
      def tryDeleteAll[A](using
          IdExtractor[A]
      ): Pipe[F, EventMessage[A], DeleteResult[A]] =
        _.evalMap { msg =>
          NonEmptyList.fromList(msg.payload.map(IdExtractor[A].getId).toList) match
            case Some(nel) =>
              logger.debug(s"Deleting documents with ids: $nel") >>
                solrClient
                  .deleteIds(nel)
                  .attempt
                  .map(DeleteResult.from(msg))

            case None =>
              Sync[F].pure(DeleteResult.NoIds(msg))
        }

      def whenSuccess[A](
          fb: EventMessage[A] => F[Unit]
      ): Pipe[F, DeleteResult[A], Unit] =
        _.evalMap {
          case DeleteResult.Success(m) =>
            logger.debug(
              s"Deletion ${m.id} successful, running post processing action"
            ) >>
              fb(m).attempt.map(DeleteResult.from(m))
          case result => result.pure[F]
        }
          .through(markProcessed)

      def deleteAll[A](using IdExtractor[A]): Pipe[F, Chunk[EventMessage[A]], Unit] =
        _.flatMap(Stream.chunk)
          .through(tryDeleteAll)
          .through(markProcessed)

      private def markProcessed[A]: Pipe[F, DeleteResult[A], Unit] =
        _.evalTap(result => reader.markProcessed(result.message.id))
          .evalMap {
            case DeleteResult.Success(_) => Sync[F].unit

            case DeleteResult.Failed(msg, err) =>
              logger.error(s"Processing messageId: ${msg.id} failed", err)

            case DeleteResult.NoIds(msg) =>
              logger.info(
                s"Not deleting from solr, since msg '${msg.id}' doesn't have document ids"
              )
          }
    }
