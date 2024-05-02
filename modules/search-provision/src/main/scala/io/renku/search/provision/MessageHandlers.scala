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
import io.renku.search.events.*
import io.renku.search.provision.handler.*
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
    add(cfg.projectCreated, makeUpsert[ProjectCreated](cfg.projectCreated).drain)

  val projectUpdated: Stream[F, Unit] =
    add(
      cfg.projectUpdated,
      makeUpsert[ProjectUpdated](cfg.projectUpdated).drain
    )

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
        .through(ps.deleteFromSolr.tryDeleteAll)
        .through(ps.deleteFromSolr.whenSuccess { msg =>
          Stream
            .emit(msg.map(_.id))
            .through(ps.userUtils.removeFromProjects)
            .compile
            .drain
        })
    )

  val groupAdded: Stream[F, Unit] =
    add(cfg.groupAdded, makeUpsert[GroupAdded](cfg.groupAdded).drain)

  val groupUpdated: Stream[F, Unit] =
    add(cfg.groupUpdated, makeUpsert[GroupUpdated](cfg.groupUpdated).drain)

  val groupRemoved: Stream[F, Unit] = {
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
          val merger = DocumentMerger[GroupRemoved]
          m.merge(merger.create, merger.merge)
        }
        .through(ps.pushToSolr.push(onConflict = retry))

    add(
      cfg.groupRemoved,
      ps.reader
        .readEvents[GroupRemoved]
        .through(ps.fetchFromSolr.fetchEntityOrPartial)
        .through(ps.deleteFromSolr.tryDeleteAll2)
        .through(ps.deleteFromSolr.whenSuccess2 { msg =>
          processMsg(msg, maxConflictRetries).compile.drain
        })
    )
  }

  val groupMemberAdded: Stream[F, Unit] =
    add(cfg.groupMemberAdded, makeUpsert[GroupMemberAdded](cfg.groupMemberAdded).drain)

  val groupMemberUpdated: Stream[F, Unit] =
    add(
      cfg.groupMemberUpdated,
      makeUpsert[GroupMemberUpdated](cfg.groupMemberUpdated).drain
    )

  // private[provision] def makeGroupChange[A](queue: QueueName)(using
  //     EventMessageDecoder[A],
  //     DocumentMerger[A],
  //     IdExtractor[A],
  //     Show[A]
  // ): Stream[F, UpsertResponse] = ???
    // val ps = steps(queue)
    // makeUpsert(queue)
    // makeUpsert
    // fetch affected projects

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
