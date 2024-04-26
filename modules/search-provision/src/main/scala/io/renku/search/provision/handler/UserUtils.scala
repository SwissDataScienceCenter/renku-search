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

package io.renku.search.provision.handler

import cats.effect.Sync
import fs2.{Pipe, Stream}

import io.renku.search.events.*
import io.renku.search.model.Id

trait UserUtils[F[_]]:
  def removeFromProjects: Pipe[F, EventMessage[Id], Unit]

object UserUtils:
  def apply[F[_]: Sync](
      fetchFromSolr: FetchFromSolr[F],
      pushToRedis: PushToRedis[F]
  ): UserUtils[F] =
    new UserUtils[F] {
      val logger = scribe.cats.effect[F]
      def removeFromProjects: Pipe[F, EventMessage[Id], Unit] =
        _.evalMap { msg =>
          (Stream.eval(
            logger.debug(s"Send authRemove events for user ids: ${msg.payload}")
          ) ++
            Stream
              .emits(msg.payload)
              .flatMap(id => fetchFromSolr.fetchProjectForUser(id).map(_ -> id))
              .map { case (projectId, userId) =>
                ProjectMemberRemoved(projectId.id, userId)
              }
              .evalTap(data => logger.info(s"Sending to redis: $data"))
              .through(
                pushToRedis.pushAuthorizationRemoved(msg.header.requestId)
              )).compile.drain
        }
    }
