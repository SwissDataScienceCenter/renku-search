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

package io.renku.search.provision.events

import cats.syntax.all.*
import io.renku.events.v1.ProjectCreated
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.solr.client.DocVersion
import io.renku.search.events.syntax.*
import io.renku.events.v1.ProjectUpdated
import io.renku.search.solr.documents.PartialEntityDocument

trait Projects:

  def fromProjectCreated(pc: ProjectCreated, version: DocVersion): ProjectDocument =
    ProjectDocument(
      id = pc.id.toId,
      `_version_` = version,
      name = pc.name.toName,
      slug = pc.slug.toSlug,
      repositories = pc.repositories.map(_.toRepository),
      visibility = pc.visibility.toModel,
      description = pc.description.map(_.toDescription),
      createdBy = pc.createdBy.toId,
      creationDate = pc.creationDate.toCreationDate
    )

  def fromProjectUpdated(pu: ProjectUpdated, orig: ProjectDocument): ProjectDocument =
    orig.copy(
      id = pu.id.toId,
      `_version_` = orig._version_,
      name = pu.name.toName,
      slug = pu.slug.toSlug,
      repositories = pu.repositories.map(_.toRepository),
      visibility = pu.visibility.toModel,
      description = pu.description.map(_.toDescription),
      score = None
    )

  def fromProjectUpdated(
      pu: ProjectUpdated,
      orig: PartialEntityDocument.Project
  ): PartialEntityDocument.Project =
    orig.copy(
      name = pu.name.toName.some,
      slug = pu.slug.toSlug.some,
      repositories = pu.repositories.map(_.toRepository),
      visibility = pu.visibility.toModel.some,
      description = pu.description.map(_.toDescription)
    )
