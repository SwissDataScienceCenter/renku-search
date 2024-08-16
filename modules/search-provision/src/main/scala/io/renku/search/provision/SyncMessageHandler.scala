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

import cats.data.OptionT
import cats.effect.*
import fs2.{Pipe, Stream}

import io.renku.search.events.*
import io.renku.search.events.SyncEventMessage.syntax.*
import io.renku.search.provision.handler.*
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.provision.process.*
import io.renku.solr.client.UpsertResponse

final class SyncMessageHandler[F[_]: Async](
    ps: PipelineSteps[F],
    maxConflictRetries: Int = 20
):
  private val genericUpsert = GenericUpsert[F](ps)
  private val genericDelete = GenericDelete[F](ps)
  private val projectUpsert = ProjectUpsert[F](ps)
  private val userDelete = UserDelete[F](ps)
  private val groupUpdate = GroupUpdate[F](ps)
  private val groupRemove = GroupRemove[F](ps)

  def create: Stream[F, Unit] = ps.reader.readSyncEvents.through(processEvent)

  // TODO: mark message as done, remove Stream.eval for evalMap, remove solr.push(conflict) and use UpsertResponse.syntax, update provision-tests
  def processEvent: Pipe[F, SyncEventMessage, Unit] =
    _.flatMap { m =>
      m.header.msgType match
        case mt: MsgType.ProjectCreated.type =>
          Stream.eval(projectUpsert.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.ProjectUpdated.type =>
          Stream.eval(projectUpsert.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.ProjectRemoved.type =>
          Stream.eval(genericDelete.process(mt.cast(m)))

        case mt: MsgType.ProjectMemberAdded.type =>
          Stream.eval(genericUpsert.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.ProjectMemberUpdated.type =>
          Stream.eval(genericUpsert.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.ProjectMemberRemoved.type =>
          Stream.eval(genericUpsert.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.UserAdded.type =>
          Stream.eval(genericUpsert.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.UserUpdated.type =>
          Stream.eval(genericUpsert.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.UserRemoved.type =>
          Stream.eval(userDelete.process(mt.cast(m)))

        case mt: MsgType.GroupAdded.type =>
          Stream.eval(genericUpsert.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.GroupUpdated.type =>
          Stream.eval(groupUpdate.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.GroupRemoved.type =>
          Stream.eval(groupRemove.process(mt.cast(m), maxConflictRetries))

        case mt: MsgType.GroupMemberAdded.type =>
          processGroupMemberUpsert(mt.cast(m))

        case mt: MsgType.GroupMemberUpdated.type =>
          processGroupMemberUpsert(mt.cast(m))

        case mt: MsgType.GroupMemberRemoved.type =>
          processGroupMemberUpsert(mt.cast(m))
    }.drain

  private def processGroupMemberUpsert[A](msg: EventMessage[A])(using
      EventMessageDecoder[A],
      DocumentMerger[A],
      IdExtractor[A]
  ): Stream[F, UpsertResponse] = {
    import cats.syntax.all.*

    def processMsg(retries: Int): Stream[F, UpsertResponse] =
      lazy val retry = OptionT.when(retries > 0)(processMsg(retries - 1))
      Stream
        .emit(msg)
        .through(ps.fetchFromSolr.fetchEntityOrPartial)
        .map { m =>
          val merger = DocumentMerger[A]
          m.merge(merger.create, merger.merge)
        }
        .through(ps.pushToSolr.push(onConflict = retry))

    def updateProjects(retries: Int): Stream[F, UpsertResponse] =
      lazy val retry = OptionT.when(retries > 0)(updateProjects(retries - 1))
      Stream
        .emit(msg)
        .through(ps.fetchFromSolr.fetchEntityOrPartial)
        .through(ps.fetchFromSolr.fetchProjectsByGroup)
        .map { m =>
          val updatedProjects =
            m.getGroups.flatMap { g =>
              m.getProjectsByGroup(g).flatMap { p =>
                msg.payload.flatMap {
                  case gma: GroupMemberAdded if gma.id == g.id =>
                    p.modifyGroupMembers(_.addMember(gma.userId, gma.role)).some

                  case gmu: GroupMemberUpdated if gmu.id == g.id =>
                    p.modifyGroupMembers(_.addMember(gmu.userId, gmu.role)).some

                  case gmr: GroupMemberRemoved if gmr.id == g.id =>
                    p.modifyGroupMembers(_.removeMember(gmr.userId)).some

                  case _ => None
                }
              }
            }

          m.setDocuments(updatedProjects).asMessage
        }
        .through(ps.pushToSolr.push(onConflict = retry))

    processMsg(maxConflictRetries) ++ updateProjects(maxConflictRetries)
  }
