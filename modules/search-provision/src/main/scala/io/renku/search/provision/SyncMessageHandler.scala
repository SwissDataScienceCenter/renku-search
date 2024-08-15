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
import io.renku.search.solr.documents.EntityMembers
import io.renku.search.solr.documents.{PartialEntityDocument, Project as ProjectDocument}
import io.renku.solr.client.UpsertResponse

final class SyncMessageHandler[F[_]: Async](
    ps: PipelineSteps[F],
    maxConflictRetries: Int = 20
):

  def create: Stream[F, Unit] = ps.reader.readSyncEvents.through(processEvent)

  def processEvent: Pipe[F, SyncEventMessage, Unit] =
    _.flatMap { m =>
      m.header.msgType match
        case mt: MsgType.ProjectCreated.type =>
          processProjectUpsertMsg(mt.cast(m), maxConflictRetries)

        case mt: MsgType.ProjectUpdated.type =>
          processProjectUpsertMsg(mt.cast(m), maxConflictRetries)

        case mt: MsgType.ProjectRemoved.type =>
          Stream.emit(mt.cast(m)).through(ps.deleteFromSolr.deleteByIds)

        case mt: MsgType.ProjectMemberAdded.type =>
          processGenericUpsert(mt.cast(m), maxConflictRetries)

        case mt: MsgType.ProjectMemberUpdated.type =>
          processGenericUpsert(mt.cast(m), maxConflictRetries)

        case mt: MsgType.ProjectMemberRemoved.type =>
          processGenericUpsert(mt.cast(m), maxConflictRetries)

        case mt: MsgType.UserAdded.type =>
          processGenericUpsert(mt.cast(m), maxConflictRetries)

        case mt: MsgType.UserUpdated.type =>
          processGenericUpsert(mt.cast(m), maxConflictRetries)

        case mt: MsgType.UserRemoved.type =>
          Stream.emit(mt.cast(m)).through(processUserRemoved)

        case mt: MsgType.GroupAdded.type =>
          processGenericUpsert(mt.cast(m), maxConflictRetries)

        case mt: MsgType.GroupUpdated.type =>
          processGroupUpdated(mt.cast(m))

        case mt: MsgType.GroupRemoved.type =>
          Stream.emit(mt.cast(m)).through(processGroupRemoved)

        case mt: MsgType.GroupMemberAdded.type =>
          processGroupMemberUpsert(mt.cast(m))

        case mt: MsgType.GroupMemberUpdated.type =>
          processGroupMemberUpsert(mt.cast(m))

        case mt: MsgType.GroupMemberRemoved.type =>
          processGroupMemberUpsert(mt.cast(m))
    }.drain

  private def processProjectUpsertMsg[A](
      msg: EventMessage[A],
      retries: Int
  )(using IdExtractor[A], DocumentMerger[A]): Stream[F, UpsertResponse] =
    lazy val retry = OptionT.when(retries > 0)(processProjectUpsertMsg(msg, retries - 1))
    Stream
      .emit(msg)
      .through(ps.fetchFromSolr.fetchEntityOrPartial)
      .map { m =>
        val merger = DocumentMerger[A]
        m.merge(merger.create, merger.merge)
      }
      .through(ps.fetchFromSolr.fetchProjectGroups)
      .map { m =>
        val updatedProjects: Seq[EntityOrPartial] =
          m.message.payload.map {
            case p: ProjectDocument =>
              p.namespace.flatMap(m.findGroupByNs) match
                case Some(group) =>
                  p.setGroupMembers(group.toEntityMembers)
                case None =>
                  p.setGroupMembers(EntityMembers.empty)
            case p: PartialEntityDocument.Project =>
              p.namespace.flatMap(m.findGroupByNs) match
                case Some(group) =>
                  p.setGroupMembers(group.toEntityMembers)
                case None =>
                  p.setGroupMembers(EntityMembers.empty)
            case e => e
          }
        m.setDocuments(updatedProjects.toList).asMessage
      }
      .through(ps.pushToSolr.push(onConflict = retry))

  private def processGenericUpsert[A](msg: EventMessage[A], retries: Int)(using
      DocumentMerger[A],
      IdExtractor[A]
  ): Stream[F, UpsertResponse] =
    lazy val retry = OptionT.when(retries > 0)(processGenericUpsert(msg, retries - 1))
    Stream
      .emit(msg)
      .through(ps.fetchFromSolr.fetchEntityOrPartial)
      .map { m =>
        val merger = DocumentMerger[A]
        m.merge(merger.create, merger.merge)
      }
      .through(ps.pushToSolr.push(onConflict = retry))

  private def processUserRemoved
      : Pipe[F, EventMessage[UserRemoved], DeleteFromSolr.DeleteResult[UserRemoved]] =
    _.map(EntityOrPartialMessage.noDocuments)
      .through(ps.deleteFromSolr.tryDeleteAll)
      .through(ps.deleteFromSolr.whenSuccess { msg =>
        Stream
          .emit(msg.message.map(_.id))
          .through(ps.userUtils.removeFromMembers)
          .compile
          .drain
      })

  private def processGroupUpdated(
      msg: EventMessage[GroupUpdated]
  ): Stream[F, UpsertResponse] = {
    def processMsg(
        msg: EntityOrPartialMessage[GroupUpdated],
        retries: Int
    ): Stream[F, UpsertResponse] =
      lazy val retry = OptionT.when(retries > 0)(processMsg(msg, retries - 1))
      Stream
        .emit(msg)
        .map { m =>
          val merger = DocumentMerger[GroupUpdated]
          m.merge(merger.create, merger.merge)
        }
        .through(ps.pushToSolr.push(onConflict = retry))

    def updateProjects(
        msg: EntityOrPartialMessage[GroupUpdated],
        retries: Int
    ): Stream[F, UpsertResponse] =
      lazy val retry = OptionT.when(retries > 0)(updateProjects(msg, retries - 1))
      Stream
        .emit(msg)
        .through(ps.fetchFromSolr.fetchProjectsByGroup)
        .map { m =>
          val updatedProjects =
            m.getGroups.flatMap { group =>
              val newNs = m.findPayloadById(group.id).map(_.namespace)
              m.getProjectsByGroup(group).map { project =>
                project.copy(namespace = newNs)
              }
            }
          m.setDocuments(updatedProjects).asMessage
        }
        .through(ps.pushToSolr.push(onConflict = retry))

    Stream
      .emit(msg)
      .through(ps.fetchFromSolr.fetchEntityOrPartial)
      .flatMap(m =>
        processMsg(m, maxConflictRetries) ++ updateProjects(m, maxConflictRetries)
      )
  }

  private def processGroupRemoved
      : Pipe[F, EventMessage[GroupRemoved], DeleteFromSolr.DeleteResult[GroupRemoved]] = {
    import io.renku.search.provision.events.syntax.*
    import io.renku.search.solr.documents.Project as ProjectDocument

    def processMsg(
        msg: EntityOrPartialMessage[GroupRemoved],
        retries: Int
    ): Stream[F, UpsertResponse] =
      lazy val retry = OptionT.when(retries > 0)(processMsg(msg, retries - 1))
      Stream
        .emit(msg)
        .through(ps.fetchFromSolr.fetchProjectsByGroup)
        .map { m =>
          m.mapToMessage {
            case p: ProjectDocument => Some(p.toPartialDocument)
            case _                  => None
          }
        }
        .through(ps.pushToSolr.push(onConflict = retry))

    _.through(ps.fetchFromSolr.fetchEntityOrPartial)
      .through(ps.deleteFromSolr.tryDeleteAll)
      .through(ps.deleteFromSolr.whenSuccess { msg =>
        processMsg(msg, maxConflictRetries).compile.drain
      })
  }

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
