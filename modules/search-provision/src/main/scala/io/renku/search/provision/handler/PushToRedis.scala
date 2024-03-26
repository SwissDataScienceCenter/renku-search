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

import cats.Show
import cats.effect.Sync
import cats.syntax.all.*
import fs2.{Pipe, Stream}
import io.renku.avro.codec.encoders.all.given
import io.renku.events.v1.ProjectAuthorizationRemoved
import io.renku.queue.client.*
import io.renku.redis.client.{ClientId, MessageId}
import io.renku.search.provision.QueuesConfig
import scribe.Scribe

trait PushToRedis[F[_]]:
  def pushAuthorizationRemoved(
      requestId: RequestId
  )(using
      Show[ProjectAuthorizationRemoved]
  ): Pipe[F, ProjectAuthorizationRemoved, MessageId]

object PushToRedis:

  def apply[F[_]: Sync](
      queueClient: Stream[F, QueueClient[F]],
      clientId: ClientId,
      queueConfig: QueuesConfig
  ): PushToRedis[F] =
    new PushToRedis[F] {
      val logger: Scribe[F] = scribe.cats.effect[F]

      def pushAuthorizationRemoved(
          requestId: RequestId
      )(using
          Show[ProjectAuthorizationRemoved]
      ): Pipe[F, ProjectAuthorizationRemoved, MessageId] =
        _.evalMap(payload => createHeader(requestId).tupleRight(payload))
          .evalTap { case (_, payload) => logger.debug(show"Pushing $payload to redis") }
          .flatMap(enqueue)

      private def enqueue(header: MessageHeader, payload: ProjectAuthorizationRemoved) =
        queueClient.evalMap(
          _.enqueue(
            queueConfig.projectAuthorizationRemoved,
            header,
            payload
          )
        )

      private def createHeader(requestId: RequestId): F[MessageHeader] =
        MessageHeader[F](
          MessageSource(clientId.value),
          ProjectAuthorizationRemoved.SCHEMA$,
          DataContentType.Binary,
          SchemaVersion.V1,
          requestId
        )
    }
