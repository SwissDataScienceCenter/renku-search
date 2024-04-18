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
import io.renku.events.v1.*
import io.renku.events.v2.GroupAdded
import io.renku.redis.client.QueueName
import io.renku.search.events.ProjectCreated
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
) extends ShowInstances:
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
      makeUpsert[ProjectAuthorizationAdded](cfg.projectAuthorizationAdded).drain
    )

  val projectAuthUpdated: Stream[F, Unit] =
    add(
      cfg.projectAuthorizationUpdated,
      makeUpsert[ProjectAuthorizationUpdated](cfg.projectAuthorizationUpdated).drain
    )

  val projectAuthRemoved: Stream[F, Unit] = add(
    cfg.projectAuthorizationRemoved,
    makeUpsert[ProjectAuthorizationRemoved](cfg.projectAuthorizationRemoved).drain
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
        .read[UserRemoved]
        .through(ps.deleteFromSolr.tryDeleteAll)
        .through(ps.deleteFromSolr.whenSuccess { msg =>
          Stream
            .emit(msg.map(IdExtractor[UserRemoved].getId))
            .through(ps.userUtils.removeFromProjects)
            .compile
            .drain
        })
    )

  val groupAdded: Stream[F, Unit] =
    add(cfg.groupAdded, makeUpsert[GroupAdded](cfg.groupAdded).drain)

  private[provision] def makeUpsert[A](queue: QueueName)(using
      QueueMessageDecoder[F, A],
      DocumentMerger[A],
      IdExtractor[A],
      Show[A]
  ): Stream[F, UpsertResponse] =
    val ps = steps(queue)
    def processMsg(
        msg: MessageReader.Message[A],
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
      .read[A]
      .flatMap(processMsg(_, maxConflictRetries))

  private def makeRemovedSimple[A](queue: QueueName)(using
      QueueMessageDecoder[F, A],
      Show[A],
      IdExtractor[A]
  ): Stream[F, Unit] =
    val ps = steps(queue)
    ps.reader
      .read[A]
      .chunks
      .through(ps.deleteFromSolr.deleteAll)
