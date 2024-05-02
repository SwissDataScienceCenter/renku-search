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
import io.renku.search.provision.handler.DeleteFromSolr.{DeleteResult, DeleteResult2}
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.events.EventMessage
import io.renku.search.model.Id

trait DeleteFromSolr[F[_]]:
  def tryDeleteAll[A](using IdExtractor[A]): Pipe[F, EventMessage[A], DeleteResult[A]]
  def tryDeleteAll2[A]: Pipe[F, EntityOrPartialMessage[A], DeleteResult2[A]]
  def deleteAll[A](using IdExtractor[A]): Pipe[F, Chunk[EventMessage[A]], Unit]
  def whenSuccess[A](fb: EventMessage[A] => F[Unit]): Pipe[F, DeleteResult[A], Unit]
  def whenSuccess2[A](
      fb: EntityOrPartialMessage[A] => F[Unit]
  ): Pipe[F, DeleteResult2[A], Unit]

object DeleteFromSolr:
  enum DeleteResult2[A](val context: EntityOrPartialMessage[A]):
    case Success(override val context: EntityOrPartialMessage[A])
        extends DeleteResult2(context)
    case Failed(override val context: EntityOrPartialMessage[A], error: Throwable)
        extends DeleteResult2(context)
    case NoIds(override val context: EntityOrPartialMessage[A])
        extends DeleteResult2(context)

  object DeleteResult2:
    def from[A](msg: EntityOrPartialMessage[A])(
        eab: Either[Throwable, Unit]
    ): DeleteResult2[A] =
      eab.fold(err => DeleteResult2.Failed(msg, err), _ => DeleteResult2.Success(msg))

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

      def tryDeleteAll2[A]: Pipe[F, EntityOrPartialMessage[A], DeleteResult2[A]] =
        _.evalMap { msg =>
          NonEmptyList.fromList(msg.documents.keySet.toList) match
            case Some(nel) =>
              logger.debug(s"Deleting documents with ids: $nel") >>
                solrClient
                  .deleteIds(nel)
                  .attempt
                  .map(DeleteResult2.from(msg))

            case None => Sync[F].pure(DeleteResult2.NoIds(msg))
        }

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

      def whenSuccess2[A](
          fb: EntityOrPartialMessage[A] => F[Unit]
      ): Pipe[F, DeleteResult2[A], Unit] =
        _.evalMap {
          case DeleteResult2.Success(m) =>
            logger.debug(
              s"Deletion ${m.message.id} successful, running post processing action"
            ) >>
              fb(m).attempt.map(DeleteResult2.from(m))
          case result => result.pure[F]
        }
          .through(markProcessed2)

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

      private def markProcessed2[A]: Pipe[F, DeleteResult2[A], Unit] =
        _.evalTap(result => reader.markProcessed(result.context.message.id))
          .evalMap {
            case DeleteResult2.Success(_) => Sync[F].unit

            case DeleteResult2.Failed(msg, err) =>
              logger.error(s"Processing messageId: ${msg.message.id} failed", err)

            case DeleteResult2.NoIds(msg) =>
              logger.info(
                s"Not deleting from solr, since msg '${msg.message.id}' doesn't have document ids"
              )
          }

    }
