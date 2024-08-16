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

import io.renku.search.events.EventMessage
import io.renku.search.model.Id
import io.renku.search.provision.handler.DeleteFromSolr.DeleteResult
import io.renku.search.solr.client.SearchSolrClient

trait DeleteFromSolr[F[_]]:
  def tryDeleteAll[A]: Pipe[F, EntityOrPartialMessage[A], DeleteResult[A]]
  def deleteAll[A](using IdExtractor[A]): Pipe[F, Chunk[EventMessage[A]], Unit]
  def deleteByIds[A](using IdExtractor[A]): Pipe[F, EventMessage[A], Unit]
  def deleteDocuments[A](msg: EventMessage[A])(using IdExtractor[A]): F[DeleteResult[A]]
  def whenSuccess[A](
      fb: EntityOrPartialMessage[A] => F[Unit]
  ): Pipe[F, DeleteResult[A], DeleteResult[A]]

object DeleteFromSolr:
  enum DeleteResult[A](val context: EntityOrPartialMessage[A]):
    case Success(override val context: EntityOrPartialMessage[A])
        extends DeleteResult(context)
    case Failed(override val context: EntityOrPartialMessage[A], error: Throwable)
        extends DeleteResult(context)
    case NoIds(override val context: EntityOrPartialMessage[A])
        extends DeleteResult(context)

  object DeleteResult:
    def from[A](msg: EntityOrPartialMessage[A])(
        eab: Either[Throwable, Unit]
    ): DeleteResult[A] =
      eab.fold(err => DeleteResult.Failed(msg, err), _ => DeleteResult.Success(msg))

  def apply[F[_]: Sync](
      solrClient: SearchSolrClient[F],
      reader: MessageReader[F]
  ): DeleteFromSolr[F] =
    new DeleteFromSolr[F] {
      val logger = scribe.cats.effect[F]

      def tryDeleteAll[A]: Pipe[F, EntityOrPartialMessage[A], DeleteResult[A]] =
        _.evalMap { msg =>
          NonEmptyList.fromList(msg.getIds.toList) match
            case Some(nel) =>
              logger.debug(s"Deleting documents with ids: $nel") >>
                solrClient
                  .deleteIds(nel)
                  .attempt
                  .map(DeleteResult.from(msg))

            case None => Sync[F].pure(DeleteResult.NoIds(msg))
        }

      def deleteDocuments[A](msg: EventMessage[A])(using
          IdExtractor[A]
      ): F[DeleteResult[A]] =
        NonEmptyList.fromList(msg.payload.map(IdExtractor[A].getId).toList) match
          case Some(nel) =>
            logger.debug(s"Deleting documents with ids: $nel") >>
              solrClient
                .deleteIds(nel)
                .attempt
                .map(DeleteResult.from(EntityOrPartialMessage.noDocuments(msg)))
          case None =>
            Sync[F].pure(DeleteResult.NoIds(EntityOrPartialMessage.noDocuments(msg)))

      def whenSuccess[A](
          fb: EntityOrPartialMessage[A] => F[Unit]
      ): Pipe[F, DeleteResult[A], DeleteResult[A]] =
        _.evalMap {
          case DeleteResult.Success(m) =>
            logger.debug(
              s"Deletion ${m.message.id} successful, running post processing action"
            ) >>
              fb(m).attempt.map(DeleteResult.from(m))
          case result => result.pure[F]
        }
          .through(markProcessed)

      def deleteAll[A](using IdExtractor[A]): Pipe[F, Chunk[EventMessage[A]], Unit] =
        _.flatMap(Stream.chunk).through(deleteByIds[A])

      def deleteByIds[A](using IdExtractor[A]): Pipe[F, EventMessage[A], Unit] =
        _.map(EntityOrPartialMessage.noDocuments[A])
          .through(tryDeleteAll)
          .through(markProcessed)
          .drain

      private def markProcessed[A]: Pipe[F, DeleteResult[A], DeleteResult[A]] =
        _.evalTap(result => reader.markProcessed(result.context.message.id))
          .evalTap {
            case DeleteResult.Success(_) => Sync[F].unit

            case DeleteResult.Failed(msg, err) =>
              logger.error(s"Processing messageId: ${msg.message.id} failed", err)

            case DeleteResult.NoIds(msg) =>
              logger.info(
                s"Not deleting from solr, since msg '${msg.message.id}' doesn't have document ids"
              )
          }
    }
