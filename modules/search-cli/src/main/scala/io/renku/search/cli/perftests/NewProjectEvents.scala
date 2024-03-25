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

import cats.Monad
import cats.effect.Clock
import cats.syntax.all.*
import io.renku.avro.codec.all.given
import io.renku.events.v1.{ProjectAuthorizationAdded, ProjectCreated, UserAdded}
import io.renku.queue.client.*
import io.renku.redis.client.QueueName
import org.apache.avro.Schema

final private case class NewProjectEvents(
    projectCreated: ProjectCreated,
    users: List[UserAdded],
    authAdded: List[ProjectAuthorizationAdded]
):
  private val messageSource = MessageSource("perf-tests")

  def toQueueDelivery[F[_]: Monad: Clock: ModelTypesGenerators]: F[List[QueueDelivery]] =
    (projectToDelivery ::
      users.map(userToDelivery) :::
      authAdded.map(authToDelivery)).sequence

  private def projectToDelivery[F[_]: Monad: Clock: ModelTypesGenerators]
      : F[QueueDelivery] =
    createHeader[F](ProjectCreated.SCHEMA$)
      .map(h => QueueDelivery(QueueName("project.created"), h, projectCreated))

  private def userToDelivery[F[_]: Monad: Clock: ModelTypesGenerators](
      p: UserAdded
  ): F[QueueDelivery] =
    createHeader[F](UserAdded.SCHEMA$)
      .map(h => QueueDelivery(QueueName("user.added"), h, p))

  private def authToDelivery[F[_]: Monad: Clock: ModelTypesGenerators](
      p: ProjectAuthorizationAdded
  ): F[QueueDelivery] =
    createHeader[F](ProjectAuthorizationAdded.SCHEMA$)
      .map(h => QueueDelivery(QueueName("projectAuth.added"), h, p))

  private def createHeader[F[_]: Monad: Clock: ModelTypesGenerators](schema: Schema) =
    ModelTypesGenerators[F].generateRequestId
      .flatMap(reqId =>
        MessageHeader[F](
          messageSource,
          schema,
          DataContentType.Binary,
          SchemaVersion.V1,
          reqId
        )
      )
