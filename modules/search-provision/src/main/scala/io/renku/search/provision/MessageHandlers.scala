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

import io.renku.solr.client.UpsertResponse

/** The entry point for defining all message handlers.
  *
  * They are defined as vals to have them automatically added to a collection, to be
  * easier accessed from the main method.
  */
final class MessageHandlers[F[_]: Async](
    steps: QueueName => PipelineSteps[F],
    cfg: QueuesConfig
) extends ShowInstances:
  private val logger = scribe.cats.effect[F]
  private var tasks: Map[String, F[Unit]] = Map.empty
  private def add(queue: QueueName, task: Stream[F, Unit]): Stream[F, Unit] =
    tasks = tasks.updated(queue.name, task.compile.drain)
    task

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

  private[provision] def makeUpsert[A](queue: QueueName)(using
      QueueMessageDecoder[F, A],
      DocumentMerger[A],
      IdExtractor[A],
      Show[A]
  ): Stream[F, UpsertResponse] =
    val ps = steps(queue)
    def processMsg(
        msg: MessageReader.Message[A],
        max: Ref[F, Int]
    ): Stream[F, UpsertResponse] =
      Stream
        .emit(msg)
        .through(ps.fetchFromSolr.fetchEntityOrPartial)
        .map { m =>
          val merger = DocumentMerger[A]
          m.merge(merger.create, merger.merge)
        }
        .through(ps.pushToSolr.push(onConflict = processMsg(msg, max), maxTries = max))
    ps.reader
      .read[A]
      .flatMap(msg =>
        Stream
          .eval(Ref[F].of(15))
          .flatMap(max => processMsg(msg, max))
      )

  private[provision] def makeUpdated[A](
      queue: QueueName,
      docUpdate: (A, EntityDocument) => Option[EntityDocument]
  )(using
      QueueMessageDecoder[F, A],
      Show[A],
      IdExtractor[A]
  ): Stream[F, UpsertResponse] =
    val ps = steps(queue)
    def processMsg(
        msg: MessageReader.Message[A],
        max: Ref[F, Int]
    ): Stream[F, UpsertResponse] =
      Stream
        .emit(msg)
        .through(ps.fetchFromSolr.fetchEntity)
        .map(_.update(docUpdate).map(e => e: EntityOrPartial))
        .through(ps.pushToSolr.push(onConflict = processMsg(msg, max), maxTries = max))
    ps.reader
      .read[A]
      .flatMap(msg =>
        Stream
          .eval(Ref[F].of(15))
          .flatMap(max => processMsg(msg, max))
      )

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
