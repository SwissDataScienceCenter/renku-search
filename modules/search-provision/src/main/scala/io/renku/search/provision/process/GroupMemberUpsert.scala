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

package io.renku.search.provision.process

import cats.effect.*
import cats.syntax.all.*

import io.renku.search.events.*
import io.renku.search.provision.handler.*
import io.renku.solr.client.UpsertResponse
import io.renku.solr.client.UpsertResponse.syntax.*

/** Processes updating a group member updates.
  *
  * It does the normal "generic" update using teh data from the message. Then it must also
  * update all related projects to change their group-members according to the new data.
  *
  * This is only for GroupMember* message types.
  */
final private[provision] class GroupMemberUpsert[F[_]: Async](ps: PipelineSteps[F]):

  def process[A](msg: EventMessage[A], retries: Int)(using
      IdExtractor[A],
      DocumentMerger[A]
  ): F[UpsertResponse] = {
    def simpleUpdate(msg: EntityOrPartialMessage[A]) =
      GenericUpsert(ps).processLoaded1(msg)

    def updateProjects(em: EntityOrPartialMessage[A]) =
      for
        withProjects <- ps.fetchFromSolr.loadProjectsByGroup(em)
        updated = updateProjectGroupMembers(withProjects, msg)
        res <- ps.pushToSolr.pushAll(updated.asMessage)
      yield res

    ps.fetchFromSolr.loadEntityOrPartial[A](msg).flatMap { m =>
      simpleUpdate(m).retryOnConflict(retries).flatMap {
        case UpsertResponse.Success(_) =>
          updateProjects(m).retryOnConflict(retries)

        case r @ UpsertResponse.VersionConflict =>
          r.pure[F]
      }
    }
  }

  private def updateProjectGroupMembers[A](
      m: EntityOrPartialMessage[A],
      msg: EventMessage[A]
  ) =
    val updated =
      m.getGroups.flatMap { g =>
        m.getProjectsByGroup(g).flatMap { p =>
          msg.payload.flatMap {
            case gma: GroupMemberAdded if gma.id == g.id =>
              p.modifyGroupMembers(_.addMember(gma.userId, gma.role)).some

            case gmu: GroupMemberUpdated if gmu.id == g.id =>
              p.modifyGroupMembers(_.addMember(gmu.userId, gmu.role)).some

            case gmr: GroupMemberRemoved if gmr.id == g.id =>
              p.modifyGroupMembers(_.removeMember(gmr.userId)).some

            case _ => None
          }
        }
      }
    m.setDocuments(updated)
