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

import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.solr.client.DocVersion
import io.renku.search.solr.documents.{Project as ProjectDocument, User as UserDocument}
import io.renku.events.v1.*

trait syntax extends io.renku.search.events.syntax:

  extension (self: ProjectAuthorizationAdded)
    def toModel(version: DocVersion): PartialEntityDocument =
      Conversion.fromProjectAuthorizationAdded(self, version)

  extension (self: ProjectAuthorizationUpdated)
    def toModel(version: DocVersion): PartialEntityDocument =
      Conversion.fromProjectAuthorizationUpdated(self, version)

  extension (self: ProjectCreated)
    def toModel(version: DocVersion): ProjectDocument =
      Conversion.fromProjectCreated(self, version)

  extension (self: ProjectUpdated)
    def toModel(orig: ProjectDocument): ProjectDocument =
      Conversion.fromProjectUpdated(self, orig)
    def toModel(orig: PartialEntityDocument.Project): PartialEntityDocument.Project =
      Conversion.fromProjectUpdated(self, orig)

  extension (self: UserAdded)
    def toModel(version: DocVersion): UserDocument =
      Conversion.fromUserAdded(self, version)

  extension (self: UserUpdated)
    def toModel(orig: UserDocument): UserDocument =
      Conversion.fromUserUpdated(self, orig)

object syntax extends syntax