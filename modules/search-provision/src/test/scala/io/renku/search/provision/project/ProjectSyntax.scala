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

package io.renku.search.provision.project

import io.github.arainko.ducktape.*
import io.renku.events.v1.ProjectCreated
import io.renku.events.v1.ProjectUpdated
import io.renku.search.model.Id
import io.renku.search.provision.handler.TypeTransformers.given
import io.renku.search.solr.documents.Project
import io.renku.solr.client.DocVersion

trait ProjectSyntax:
  extension (created: ProjectCreated)
    def toSolrDocument: Project = created
      .into[Project]
      .transform(
        Field.default(_.version),
        Field.const(_.version, DocVersion.NotExists),
        Field.default(_.owners),
        Field.default(_.members),
        Field.default(_.score)
      )

    def update(updated: ProjectUpdated): ProjectCreated =
      created.copy(
        name = updated.name,
        slug = updated.slug,
        repositories = updated.repositories,
        visibility = updated.visibility,
        description = updated.description
      )
