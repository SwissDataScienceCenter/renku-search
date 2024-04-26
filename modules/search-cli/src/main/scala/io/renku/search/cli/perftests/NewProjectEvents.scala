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

package io.renku.search.cli.perftests

import cats.effect.Sync
import cats.syntax.all.*
import io.renku.search.events.*
import io.renku.redis.client.QueueName

final private case class NewProjectEvents(
    projectCreated: ProjectCreated,
    users: List[UserAdded],
    authAdded: List[ProjectMemberAdded]
):
  private val messageSource = MessageSource("perf-tests")

  def toQueueDelivery[F[_]: Sync: ModelTypesGenerators]: F[List[QueueDelivery]] =
    (projectToDelivery ::
      users.map(userToDelivery) :::
      authAdded.map(authToDelivery)).sequence

  private def projectToDelivery[F[_]: Sync: ModelTypesGenerators]: F[QueueDelivery] =
    createMessage[F, ProjectCreated](projectCreated).map(m =>
      QueueDelivery(QueueName("project.created"), m)
    )

  private def userToDelivery[F[_]: Sync: ModelTypesGenerators](
      p: UserAdded
  ): F[QueueDelivery] =
    createMessage[F, UserAdded](p).map(m => QueueDelivery(QueueName("user.added"), m))

  private def authToDelivery[F[_]: Sync: ModelTypesGenerators](
      p: ProjectMemberAdded
  ): F[QueueDelivery] =
    createMessage[F, ProjectMemberAdded](p).map(m =>
      QueueDelivery(QueueName("projectAuth.added"), m)
    )

  private def createMessage[F[_], A <: RenkuEventPayload](
      pl: A
  )(using Sync[F], ModelTypesGenerators[F]): F[EventMessage[A]] =
    ModelTypesGenerators[F].generateRequestId.flatMap { reqId =>
      EventMessage.create[F, A](
        messageSource,
        DataContentType.Binary,
        reqId,
        pl
      )
    }
