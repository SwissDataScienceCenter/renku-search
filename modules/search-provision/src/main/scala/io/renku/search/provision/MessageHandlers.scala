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
import fs2.Stream

import io.renku.redis.client.QueueName
import io.renku.search.config.QueuesConfig
import io.renku.search.provision.BackgroundProcessManage.TaskName
import io.renku.search.provision.MessageHandlers.MessageHandlerKey
import io.renku.search.provision.handler.*
import io.renku.search.provision.reindex.ReprovisionService

/** The entry point for defining all message handlers.
  *
  * They are defined as vals to have them automatically added to a collection, to be
  * easier accessed from the main method.
  */
final class MessageHandlers[F[_]: Async](
    steps: QueueName => PipelineSteps[F],
    reprovisionService: ReprovisionService[F],
    cfg: QueuesConfig,
    ctrl: SyncMessageHandler.Control[F],
    maxConflictRetries: Int = 20
):
  assert(maxConflictRetries >= 0, "maxConflictRetries must be >= 0")

  private val logger = scribe.cats.effect[F]
  private var tasks: Map[TaskName, F[Unit]] = Map.empty
  private def add[A](name: MessageHandlerKey, task: Stream[F, A]): Stream[F, Unit] =
    tasks = tasks.updated(name, task.compile.drain)
    task.void

  private[provision] def withMaxConflictRetries(n: Int): MessageHandlers[F] =
    new MessageHandlers[F](steps, reprovisionService, cfg, ctrl, n)

  def getAll: Map[TaskName, F[Unit]] = tasks

  private[provision] def createHandler(qn: QueueName): SyncMessageHandler[F] =
    new SyncMessageHandler(steps(qn), reprovisionService, ctrl, maxConflictRetries)

  val allEvents = add(
    MessageHandlerKey.DataServiceAllEvents,
    createHandler(cfg.dataServiceAllEvents).create
  )

  val projectCreated: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectCreated,
      createHandler(cfg.projectCreated).create
    )

  val projectUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectUpdated,
      createHandler(cfg.projectUpdated).create
    )

  val projectRemoved: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectRemoved,
      createHandler(cfg.projectRemoved).create
    )

  val projectAuthAdded: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectAuthorizationAdded,
      createHandler(cfg.projectAuthorizationAdded).create
    )

  val projectAuthUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectAuthorizationUpdated,
      createHandler(cfg.projectAuthorizationUpdated).create
    )

  val projectAuthRemoved: Stream[F, Unit] = add(
    MessageHandlerKey.ProjectAuthorizationRemoved,
    createHandler(cfg.projectAuthorizationRemoved).create
  )

  val userAdded: Stream[F, Unit] =
    add(
      MessageHandlerKey.UserAdded,
      createHandler(cfg.userAdded).create
    )

  val userUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.UserUpdated,
      createHandler(cfg.userUpdated).create
    )

  val userRemoved: Stream[F, Unit] =
    add(
      MessageHandlerKey.UserRemoved,
      createHandler(cfg.userRemoved).create
    )

  val groupAdded: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupAdded,
      createHandler(cfg.groupAdded).create
    )

  val groupUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupUpdated,
      createHandler(cfg.groupUpdated).create
    )

  val groupRemove: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupRemoved,
      createHandler(cfg.groupRemoved).create
    )

  val groupMemberAdded: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupMemberAdded,
      createHandler(cfg.groupMemberAdded).create
    )

  val groupMemberUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupMemberUpdated,
      createHandler(cfg.groupMemberUpdated).create
    )

  val groupMemberRemoved: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupMemberRemoved,
      createHandler(cfg.groupMemberRemoved).create
    )

object MessageHandlers:

  enum MessageHandlerKey extends TaskName:
    case DataServiceAllEvents
    case GroupMemberRemoved
    case GroupMemberUpdated
    case GroupMemberAdded
    case GroupRemoved
    case GroupUpdated
    case GroupAdded
    case UserRemoved
    case UserAdded
    case UserUpdated
    case ProjectAuthorizationRemoved
    case ProjectAuthorizationUpdated
    case ProjectAuthorizationAdded
    case ProjectRemoved
    case ProjectUpdated
    case ProjectCreated

  object MessageHandlerKey:
    def isInstance(tn: TaskName): Boolean = tn match
      case _: MessageHandlerKey => true
      case _                    => false
