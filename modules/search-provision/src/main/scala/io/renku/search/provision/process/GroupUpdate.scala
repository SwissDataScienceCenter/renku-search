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

/** Processes updating a group.
  *
  * This is first doing a [[GenericUpsert]] and then for all projects in that groups
  * namespace, update its namespace property to that of the corresponding group. This is
  * necessary if a group has updated its namespace. Then all their projects must be
  * renamed.
  */
final private[provision] class GroupUpdate[F[_]: Async](ps: PipelineSteps[F]):

  def process(
      msg: EventMessage[GroupUpdated],
      retries: Int
  ): F[UpsertResponse] = {
    def simpleUpdate(msg: EntityOrPartialMessage[GroupUpdated]) =
      GenericUpsert(ps).processLoaded1(msg)

    def updateProjects(msg: EntityOrPartialMessage[GroupUpdated]) =
      for
        withProjects <- ps.fetchFromSolr.loadProjectsByGroup(msg)
        updated = updateProjectNamespace(withProjects)
        res <- ps.pushToSolr.pushAll(updated.asMessage)
      yield res

    ps.fetchFromSolr.loadEntityOrPartial[GroupUpdated](msg).flatMap { m =>
      simpleUpdate(m).retryOnConflict(retries).flatMap {
        case UpsertResponse.Success(_) =>
          updateProjects(m).retryOnConflict(retries)

        case r @ UpsertResponse.VersionConflict =>
          r.pure[F]
      }
    }
  }

  /** For each group loaded from database, the updated data from the message is looked up
    * and the new namespace retrieved. Then the payload is iterated through every project
    * that is in the "old group" and the projects namespace is updated to the new
    * namespace.
    */
  private def updateProjectNamespace(m: EntityOrPartialMessage[GroupUpdated]) =
    val updatedProjects =
      m.getGroups.flatMap { groupAtDb =>
        val newNamespace = m.findPayloadById(groupAtDb.id).map(_.namespace)
        m.getProjectsByGroup(groupAtDb).flatMap { project =>
          if (project.namespace != newNamespace)
            project.copy(namespace = newNamespace).some
          else None
        }
      }
    m.setDocuments(updatedProjects)
