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
  private var tasks: Map[TaskName, F[Unit]] = Map.empty
  private def add[A](name: MessageHandlerKey, task: Stream[F, A]): Stream[F, Unit] =
    tasks = tasks.updated(name, task.compile.drain)
    task.void

  private[provision] def withMaxConflictRetries(n: Int): MessageHandlers[F] =
    new MessageHandlers[F](steps, cfg, n)

  def getAll: Map[TaskName, F[Unit]] = tasks

  val allEvents = add(
    MessageHandlerKey.DataServiceAllEvents,
    SyncMessageHandler(steps(cfg.dataServiceAllEvents), maxConflictRetries).create
  )

  val projectCreated: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectCreated,
      SyncMessageHandler(steps(cfg.projectCreated), maxConflictRetries).create
    )

  val projectUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectUpdated,
      SyncMessageHandler(steps(cfg.projectUpdated), maxConflictRetries).create
    )

  val projectRemoved: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectRemoved,
      SyncMessageHandler(steps(cfg.projectRemoved), maxConflictRetries).create
    )

  val projectAuthAdded: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectAuthorizationAdded,
      SyncMessageHandler(steps(cfg.projectAuthorizationAdded), maxConflictRetries).create
    )

  val projectAuthUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.ProjectAuthorizationUpdated,
      SyncMessageHandler(
        steps(cfg.projectAuthorizationUpdated),
        maxConflictRetries
      ).create
    )

  val projectAuthRemoved: Stream[F, Unit] = add(
    MessageHandlerKey.ProjectAuthorizationRemoved,
    SyncMessageHandler(steps(cfg.projectAuthorizationRemoved), maxConflictRetries).create
  )

  val userAdded: Stream[F, Unit] =
    add(
      MessageHandlerKey.UserAdded,
      SyncMessageHandler(steps(cfg.userAdded), maxConflictRetries).create
    )

  val userUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.UserUpdated,
      SyncMessageHandler(steps(cfg.userUpdated), maxConflictRetries).create
    )

  val userRemoved: Stream[F, Unit] =
    add(
      MessageHandlerKey.UserRemoved,
      SyncMessageHandler(steps(cfg.userRemoved), maxConflictRetries).create
    )

  val groupAdded: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupAdded,
      SyncMessageHandler(steps(cfg.groupAdded), maxConflictRetries).create
    )

  val groupUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupUpdated,
      SyncMessageHandler(steps(cfg.groupUpdated), maxConflictRetries).create
    )

  val groupRemove: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupRemoved,
      SyncMessageHandler(steps(cfg.groupRemoved), maxConflictRetries).create
    )

  val groupMemberAdded: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupMemberAdded,
      SyncMessageHandler(steps(cfg.groupMemberAdded), maxConflictRetries).create
    )

  val groupMemberUpdated: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupMemberUpdated,
      SyncMessageHandler(steps(cfg.groupMemberUpdated), maxConflictRetries).create
    )

  val groupMemberRemoved: Stream[F, Unit] =
    add(
      MessageHandlerKey.GroupMemberRemoved,
      SyncMessageHandler(steps(cfg.groupMemberRemoved), maxConflictRetries).create
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
