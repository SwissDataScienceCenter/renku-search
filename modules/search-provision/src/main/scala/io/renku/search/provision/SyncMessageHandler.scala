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

import cats.effect.*
import cats.syntax.all.*
import fs2.{Pipe, Stream}

import io.renku.search.events.*
import io.renku.search.events.SyncEventMessage.syntax.*
import io.renku.search.provision.handler.*
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.provision.process.*
import scribe.Scribe
import io.renku.solr.client.UpsertResponse
import io.renku.search.provision.handler.DeleteFromSolr.DeleteResult
import SyncMessageHandler.Result

final class SyncMessageHandler[F[_]: Async](
    ps: PipelineSteps[F],
    maxConflictRetries: Int = 20
):

  private given logger: Scribe[F] = scribe.cats.effect[F]

  private val genericUpsert = GenericUpsert[F](ps)
  private val genericDelete = GenericDelete[F](ps)
  private val projectUpsert = ProjectUpsert[F](ps)
  private val userDelete = UserDelete[F](ps)
  private val groupUpdate = GroupUpdate[F](ps)
  private val groupRemove = GroupRemove[F](ps)
  private val groupMemberUpsert = GroupMemberUpsert[F](ps)

  def create: Stream[F, Result] = ps.reader.readSyncEvents.through(processEvents)

  def processEvents: Pipe[F, SyncEventMessage, Result] =
    _.evalMap(processEvent)

  def processEvent(m: SyncEventMessage): F[Result] =
    m.header.msgType match
      case mt: MsgType.ProjectCreated.type =>
        markMessage(m)(
          projectUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.ProjectUpdated.type =>
        markMessage(m)(
          projectUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.ProjectRemoved.type =>
        markMessage(m)(genericDelete.process(mt.cast(m)).map(Result.Delete.apply))

      case mt: MsgType.ProjectMemberAdded.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.ProjectMemberUpdated.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.ProjectMemberRemoved.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.UserAdded.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.UserUpdated.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.UserRemoved.type =>
        markMessage(m)(userDelete.process(mt.cast(m)).map(Result.Delete.apply))

      case mt: MsgType.GroupAdded.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.GroupUpdated.type =>
        markMessage(m)(
          groupUpdate.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.GroupRemoved.type =>
        markMessage(m)(
          groupRemove.process(mt.cast(m), maxConflictRetries).map(Result.Delete.apply)
        )

      case mt: MsgType.GroupMemberAdded.type =>
        markMessage(m)(
          groupMemberUpsert
            .process(mt.cast(m), maxConflictRetries)
            .map(Result.Upsert.apply)
        )

      case mt: MsgType.GroupMemberUpdated.type =>
        markMessage(m)(
          groupMemberUpsert
            .process(mt.cast(m), maxConflictRetries)
            .map(Result.Upsert.apply)
        )

      case mt: MsgType.GroupMemberRemoved.type =>
        markMessage(m)(
          groupMemberUpsert
            .process(mt.cast(m), maxConflictRetries)
            .map(Result.Upsert.apply)
        )

  private def markMessage[A](m: SyncEventMessage)(fa: F[A]): F[A] =
    Resource
      .onFinalizeCase[F] {
        case Resource.ExitCase.Succeeded   => ps.reader.markProcessed(m.id)
        case Resource.ExitCase.Canceled    => ps.reader.markProcessed(m.id)
        case Resource.ExitCase.Errored(ex) => ps.reader.markProcessedError(ex, m.id)
      }
      .use(_ => fa)

object SyncMessageHandler:

  enum Result:
    case Upsert(value: UpsertResponse)
    case Delete(value: DeleteFromSolr.DeleteResult[?])

    def fold[A](fu: UpsertResponse => A, fd: DeleteFromSolr.DeleteResult[?] => A): A =
      this match
        case Upsert(r) => fu(r)
        case Delete(r) => fd(r)

    def asUpsert: Option[UpsertResponse] = fold(Some(_), _ => None)
