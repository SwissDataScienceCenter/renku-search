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
import cats.effect.*
import fs2.Stream

import io.renku.events.v1.*
import io.renku.redis.client.QueueName
import io.renku.search.provision.handler.*
import io.renku.search.solr.documents.EntityDocument

/** The entry point for defining all message handlers.
  *
  * They are defined as vals to have them automatically added to a collection, to be
  * easier accessed from the main method.
  */
final class MessageHandlers[F[_]: Async](
    steps: QueueName => PipelineSteps[F],
    cfg: QueuesConfig
) extends ShowInstances:
  private[this] var tasks: Map[String, F[Unit]] = Map.empty
  private[this] def add(queue: QueueName, task: Stream[F, Unit]): Stream[F, Unit] =
    tasks = tasks.updated(queue.name, task.compile.drain)
    task

  def getAll: Map[String, F[Unit]] = tasks

  val projectCreated: Stream[F, Unit] =
    add(cfg.projectCreated, makeCreated[ProjectCreated](cfg.projectCreated))

  val projectUpdated =
    add(
      cfg.projectUpdated,
      makeUpdated[ProjectUpdated](cfg.projectUpdated, DocumentUpdates.project)
    )

  val projectRemoved: Stream[F, Unit] =
    add(cfg.projectRemoved, makeRemovedSimple[ProjectRemoved](cfg.projectRemoved))

  val projectAuthAdded: Stream[F, Unit] =
    add(
      cfg.projectAuthorizationAdded,
      makeUpdated[ProjectAuthorizationAdded](
        cfg.projectAuthorizationAdded,
        DocumentUpdates.projectAuthAdded
      )
    )

  val projectAuthUpdated: Stream[F, Unit] =
    add(
      cfg.projectAuthorizationUpdated,
      makeUpdated[ProjectAuthorizationUpdated](
        cfg.projectAuthorizationUpdated,
        DocumentUpdates.projectAuthUpdated
      )
    )

  val projectAuthRemoved: Stream[F, Unit] = add(
    cfg.projectAuthorizationRemoved,
    makeUpdated[ProjectAuthorizationRemoved](
      cfg.projectAuthorizationRemoved,
      DocumentUpdates.projectAuthRemoved
    )
  )

  val userAdded: Stream[F, Unit] =
    add(cfg.userAdded, makeCreated[UserAdded](cfg.userAdded))

  val userUpdated =
    add(cfg.userUpdated, makeUpdated[UserUpdated](cfg.userUpdated, DocumentUpdates.user))

  val userRemoved =
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

  private def makeCreated[A](queue: QueueName)(using
      QueueMessageDecoder[F, A],
      DocumentConverter[A],
      Show[A]
  ): Stream[F, Unit] =
    val ps = steps(queue)
    ps.reader
      .read[A]
      .chunks
      .through(ps.converter.convertChunk)
      .through(ps.pushToSolr.pushChunk)

  private def makeUpdated[A](
      queue: QueueName,
      docUpdate: (A, EntityDocument) => Option[EntityDocument]
  )(using
      QueueMessageDecoder[F, A],
      Show[A],
      IdExtractor[A]
  ): Stream[F, Unit] =
    val ps = steps(queue)
    ps.reader
      .read[A]
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
      .read[A]
      .chunks
      .through(ps.deleteFromSolr.deleteAll)