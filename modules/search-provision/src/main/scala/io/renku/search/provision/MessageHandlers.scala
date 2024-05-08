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

import cats.Show
import cats.data.OptionT
import cats.effect.*
import fs2.Stream

import io.renku.redis.client.QueueName
import io.renku.search.config.QueuesConfig
import io.renku.search.events.*
import io.renku.search.provision.handler.*
import io.renku.search.solr.documents.EntityMembers
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.solr.client.UpsertResponse

/** The entry point for defining all message handlers.
  *
  * They are defined as vals to have them automatically added to a collection, to be
  * easier accessed from the main method.
  */
final class MessageHandlers[F[_]: Async](
    steps: QueueName => PipelineSteps[F],
    cfg: QueuesConfig,
    maxConflictRetries: Int = 20
):
  assert(maxConflictRetries >= 0, "maxConflictRetries must be >= 0")

  private val logger = scribe.cats.effect[F]
  private var tasks: Map[String, F[Unit]] = Map.empty
  private def add(queue: QueueName, task: Stream[F, Unit]): Stream[F, Unit] =
    tasks = tasks.updated(queue.name, task.compile.drain)
    task

  private[provision] def withMaxConflictRetries(n: Int): MessageHandlers[F] =
    new MessageHandlers[F](steps, cfg, n)

  def getAll: Map[String, F[Unit]] = tasks

  val projectCreated: Stream[F, Unit] =
    add(cfg.projectCreated, makeProjectUpsert[ProjectCreated](cfg.projectCreated).drain)

  val projectUpdated: Stream[F, Unit] =
    add(
      cfg.projectUpdated,
      makeProjectUpsert[ProjectUpdated](cfg.projectUpdated).drain
    )

  private[provision] def makeProjectUpsert[A](queue: QueueName)(using
      EventMessageDecoder[A],
      DocumentMerger[A],
      IdExtractor[A],
      Show[A]
  ): Stream[F, UpsertResponse] = {
    import io.renku.search.solr.documents.Project as ProjectDocument

    val ps = steps(queue)
    def processMsg(
        msg: EventMessage[A],
        retries: Int
    ): Stream[F, UpsertResponse] =
      lazy val retry = OptionT.when(retries > 0)(processMsg(msg, retries - 1))
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

    ps.reader
      .readEvents[A]
      .flatMap(processMsg(_, maxConflictRetries))
  }

  val projectRemoved: Stream[F, Unit] =
    add(cfg.projectRemoved, makeRemovedSimple[ProjectRemoved](cfg.projectRemoved))

  val projectAuthAdded: Stream[F, Unit] =
    add(
      cfg.projectAuthorizationAdded,
      makeUpsert[ProjectMemberAdded](cfg.projectAuthorizationAdded).drain
    )

  val projectAuthUpdated: Stream[F, Unit] =
    add(
      cfg.projectAuthorizationUpdated,
      makeUpsert[ProjectMemberUpdated](cfg.projectAuthorizationUpdated).drain
    )

  val projectAuthRemoved: Stream[F, Unit] = add(
    cfg.projectAuthorizationRemoved,
    makeUpsert[ProjectMemberRemoved](cfg.projectAuthorizationRemoved).drain
  )

  val userAdded: Stream[F, Unit] =
    add(cfg.userAdded, makeUpsert[UserAdded](cfg.userAdded).drain)

  val userUpdated: Stream[F, Unit] =
    add(cfg.userUpdated, makeUpsert[UserUpdated](cfg.userUpdated).drain)

  val userRemoved: Stream[F, Unit] =
    val ps = steps(cfg.userRemoved)
    add(
      cfg.userRemoved,
      ps.reader
        .readEvents[UserRemoved]
        .map(EntityOrPartialMessage.noDocuments)
        .through(ps.deleteFromSolr.tryDeleteAll)
        .through(ps.deleteFromSolr.whenSuccess { msg =>
          Stream
            .emit(msg.message.map(_.id))
            .through(ps.userUtils.removeFromProjects)
            .compile
            .drain
        })
        .drain
    )

  val groupAdded: Stream[F, Unit] =
    add(cfg.groupAdded, makeUpsert[GroupAdded](cfg.groupAdded).drain)

  private[provision] def makeGroupUpdated(queue: QueueName): Stream[F, UpsertResponse] = {
    val ps = steps(queue)
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

    ps.reader
      .readEvents[GroupUpdated]
      .through(ps.fetchFromSolr.fetchEntityOrPartial)
      .flatMap(m =>
        processMsg(m, maxConflictRetries) ++ updateProjects(m, maxConflictRetries)
      )
  }

  val groupUpdated: Stream[F, Unit] =
    add(cfg.groupUpdated, makeGroupUpdated(cfg.groupUpdated).drain)

  private[provision] val makeGroupRemoved
      : Stream[F, DeleteFromSolr.DeleteResult[GroupRemoved]] = {
    import io.renku.search.provision.events.syntax.*
    import io.renku.search.solr.documents.Project as ProjectDocument

    val ps = steps(cfg.groupRemoved)
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
    ps.reader
      .readEvents[GroupRemoved]
      .through(ps.fetchFromSolr.fetchEntityOrPartial)
      .through(ps.deleteFromSolr.tryDeleteAll)
      .through(ps.deleteFromSolr.whenSuccess { msg =>
        processMsg(msg, maxConflictRetries).compile.drain
      })
  }

  val groupRemove: Stream[F, Unit] =
    add(cfg.groupRemoved, makeGroupRemoved.drain)

  val groupMemberAdded: Stream[F, Unit] =
    add(
      cfg.groupMemberAdded,
      makeGroupMemberUpsert[GroupMemberAdded](cfg.groupMemberAdded).drain
    )

  val groupMemberUpdated: Stream[F, Unit] =
    add(
      cfg.groupMemberUpdated,
      makeGroupMemberUpsert[GroupMemberUpdated](cfg.groupMemberUpdated).drain
    )

  val groupMemberRemoved: Stream[F, Unit] =
    add(
      cfg.groupMemberRemoved,
      makeGroupMemberUpsert[GroupMemberRemoved](cfg.groupMemberRemoved).drain
    )

  private[provision] def makeUpsert[A](queue: QueueName)(using
      EventMessageDecoder[A],
      DocumentMerger[A],
      IdExtractor[A],
      Show[A]
  ): Stream[F, UpsertResponse] =
    val ps = steps(queue)
    def processMsg(
        msg: EventMessage[A],
        retries: Int
    ): Stream[F, UpsertResponse] =
      lazy val retry = OptionT.when(retries > 0)(processMsg(msg, retries - 1))
      Stream
        .emit(msg)
        .through(ps.fetchFromSolr.fetchEntityOrPartial)
        .map { m =>
          val merger = DocumentMerger[A]
          m.merge(merger.create, merger.merge)
        }
        .through(ps.pushToSolr.push(onConflict = retry))

    ps.reader
      .readEvents[A]
      .flatMap(processMsg(_, maxConflictRetries))

  private[provision] def makeGroupMemberUpsert[A](queue: QueueName)(using
      EventMessageDecoder[A],
      DocumentMerger[A],
      IdExtractor[A],
      Show[A]
  ): Stream[F, UpsertResponse] = {
    import cats.syntax.all.*

    val ps = steps(queue)
    def processMsg(
        msg: EventMessage[A],
        retries: Int
    ): Stream[F, UpsertResponse] =
      lazy val retry = OptionT.when(retries > 0)(processMsg(msg, retries - 1))
      Stream
        .emit(msg)
        .through(ps.fetchFromSolr.fetchEntityOrPartial)
        .map { m =>
          val merger = DocumentMerger[A]
          m.merge(merger.create, merger.merge)
        }
        .through(ps.pushToSolr.push(onConflict = retry))

    def updateProjects(msg: EventMessage[A], retries: Int): Stream[F, UpsertResponse] =
      lazy val retry = OptionT.when(retries > 0)(updateProjects(msg, retries - 1))
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
    ps.reader
      .readEvents[A]
      .flatMap(m =>
        processMsg(m, maxConflictRetries) ++ updateProjects(m, maxConflictRetries)
      )
  }

  private def makeRemovedSimple[A](queue: QueueName)(using
      EventMessageDecoder[A],
      Show[A],
      IdExtractor[A]
  ): Stream[F, Unit] =
    val ps = steps(queue)
    ps.reader
      .readEvents[A]
      .chunks
      .through(ps.deleteFromSolr.deleteAll)
