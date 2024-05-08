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

import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all.*
import fs2.{Pipe, Stream}

import io.renku.search.events.*
import io.renku.search.model.Id
import io.renku.search.solr.documents.{
  Group as GroupDocument,
  PartialEntityDocument,
  Project as ProjectDocument
}
import io.renku.solr.client.UpsertResponse

trait UserUtils[F[_]]:
  def removeFromMembers: Pipe[F, EventMessage[Id], FetchFromSolr.EntityId]

object UserUtils:
  def apply[F[_]: Sync](
      fetchFromSolr: FetchFromSolr[F],
      pushToSolr: PushToSolr[F],
      reader: MessageReader[F],
      maxConflictRetries: Int
  ): UserUtils[F] =
    new UserUtils[F] {
      val logger = scribe.cats.effect[F]

      private def updateEntity(
          id: Id,
          modify: EntityOrPartial => Option[EntityOrPartial],
          retries: Int = maxConflictRetries
      ): Stream[F, UpsertResponse] =
        val retry = OptionT.when(retries > 0)(updateEntity(id, modify, retries - 1))
        Stream
          .eval(fetchFromSolr.fetchEntityOrPartialById(id))
          .unNone
          .map(modify)
          .unNone
          .through(pushToSolr.push1(onConflict = retry))

      def removeFromMembers: Pipe[F, EventMessage[Id], FetchFromSolr.EntityId] =
        _.flatMap { msg =>
          Stream
            .emits(msg.payload)
            .flatMap(fetchFromSolr.fetchEntityForUser)
            .evalTap { entityId =>
              updateEntity(
                entityId.id,
                {
                  case p: ProjectDocument =>
                    p.modifyEntityMembers(_.removeMembers(msg.payload))
                      .modifyGroupMembers(_.removeMembers(msg.payload))
                      .some
                  case p: PartialEntityDocument.Project =>
                    p.modifyEntityMembers(_.removeMembers(msg.payload))
                      .modifyGroupMembers(_.removeMembers(msg.payload))
                      .some
                  case g: GroupDocument =>
                    g.modifyEntityMembers(_.removeMembers(msg.payload)).some

                  case _ => None
                }
              ).compile.drain
            }
            .through(reader.markMessageOnDone(msg.id)(using logger))
        }
    }
