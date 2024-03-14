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

package io.renku.search.provision.variant

import scala.concurrent.duration.*

import cats.Show
import cats.effect.*
import fs2.Stream

import io.renku.events.v1.*
import io.renku.redis.client.QueueName
import io.renku.search.provision.QueueMessageDecoder
import io.renku.search.provision.QueuesConfig
import io.renku.search.solr.documents.Entity as Document

final class MessageHandlers[F[_]: Async](
    steps: QueueName => PipelineSteps[F],
    cfg: QueuesConfig,
    chunkSize: Int = 10,
    timeout: FiniteDuration = 500.millis
) extends ShowInstances:

  def projectCreated: Stream[F, Unit] = makeCreated[ProjectCreated](cfg.projectCreated)

  def projectUpdated =
    makeUpdated[ProjectUpdated](cfg.projectUpdated, DocumentUpdates.project)

  def projectRemoved: Stream[F, Unit] =
    makeRemovedSimple[ProjectRemoved](cfg.projectRemoved)

  def projectAuthAdded: Stream[F, Unit] = makeUpdated[ProjectAuthorizationAdded](
    cfg.projectAuthorizationAdded,
    DocumentUpdates.projectAuthAdded
  )

  def projectAuthUpdated: Stream[F, Unit] = makeUpdated[ProjectAuthorizationUpdated](
    cfg.projectAuthorizationUpdated,
    DocumentUpdates.projectAuthUpdated
  )

  def projectAuthRemoved: Stream[F, Unit] = makeUpdated[ProjectAuthorizationRemoved](
    cfg.projectAuthorizationRemoved,
    DocumentUpdates.projectAuthRemoved
  )

  def userAdded: Stream[F, Unit] = makeCreated[UserAdded](cfg.userAdded)

  def userUpdated = makeUpdated[UserUpdated](cfg.userUpdated, DocumentUpdates.user)

  def userRemoved =
    val ps = steps(cfg.userRemoved)
    ps.reader
      .readGrouped[UserRemoved](chunkSize, timeout)
      .flatMap(Stream.chunk)
      .through(ps.deleteFromSolr.tryDeleteAll)
      .through(ps.deleteFromSolr.whenSuccess { msg =>
        val userIds = msg.decoded.map(IdExtractor[UserRemoved].getId)
        Stream
          .emits(userIds)
          .flatMap(id => ps.fetchFromSolr.fetchProjectForUser(id).map(_ -> id))
          .map { case (projectId, userId) =>
            ProjectAuthorizationRemoved(projectId.id.value, userId.value)
          }
          .through(ps.pushToRedis.pushAuthorizationRemoved(msg.requestId))
          .compile
          .drain
      })

  private def makeCreated[A](queue: QueueName)(using
      QueueMessageDecoder[F, A],
      DocumentConverter[A],
      Show[A]
  ): Stream[F, Unit] =
    val ps = steps(queue)
    ps.reader
      .readGrouped[A](chunkSize, timeout)
      .through(ps.converter.convertChunk)
      .through(ps.pushToSolr.pushChunk)

  private def makeUpdated[A](queue: QueueName, docUpdate: (A, Document) => Document)(using
      QueueMessageDecoder[F, A],
      Show[A],
      IdExtractor[A]
  ): Stream[F, Unit] =
    val ps = steps(queue)
    ps.reader
      .readGrouped[A](chunkSize, timeout)
      .flatMap(Stream.chunk)
      .through(ps.fetchFromSolr.fetch1)
      .map(_.update(docUpdate))
      .through(ps.pushToSolr.push)

  private def makeRemovedSimple[A](queue: QueueName)(using
      QueueMessageDecoder[F, A],
      Show[A],
      IdExtractor[A]
  ): Stream[F, Unit] =
    val ps = steps(queue)
    ps.reader
      .readGrouped[A](chunkSize, timeout)
      .through(ps.deleteFromSolr.deleteAll)
