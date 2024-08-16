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
import io.renku.search.provision.events.syntax.*
import io.renku.search.provision.handler.*
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.solr.client.UpsertResponse
import io.renku.solr.client.UpsertResponse.syntax.*

/** Processes removing a group.
  *
  * When a group is removed, all projects that are currently available in that group are
  * moved to a "partial" entity. That way the data is still in the index, but it will not
  * be returned from the search api.
  */
final class GroupRemove[F[_]: Async](ps: PipelineSteps[F]):
  private val logger = scribe.cats.effect[F]

  def process(
      msg: EventMessage[GroupRemoved],
      retries: Int
  ): F[DeleteFromSolr.DeleteResult[GroupRemoved]] =
    for
      loaded <- ps.fetchFromSolr.loadEntityOrPartial[GroupRemoved](msg)
      delRes <- ps.deleteFromSolr.deleteDocuments(msg)
      _ <- delRes match
        case DeleteFromSolr.DeleteResult.Success(_) =>
          convertProjectsToPartial(loaded).retryOnConflict(retries).flatMap {
            case UpsertResponse.VersionConflict =>
              logger.warn(
                s"Hiding projects of a removed group '$msg' failed due to version conflicts."
              )
            case UpsertResponse.Success(_) => ().pure[F]
          }
        case _ =>
          ().pure[F]
    yield ???

  private def convertProjectsToPartial(
      msg: EntityOrPartialMessage[GroupRemoved]
  ): F[UpsertResponse] =
    for
      withProjects <- ps.fetchFromSolr.loadProjectsByGroup(msg)
      updated = withProjects.mapToMessage {
        case p: ProjectDocument => p.toPartialDocument.some
        case _                  => None
      }
      res <- ps.pushToSolr.pushAll(updated)
    yield res
