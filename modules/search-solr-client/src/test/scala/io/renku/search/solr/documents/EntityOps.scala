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

package io.renku.search.solr.documents

import io.renku.search.model.Version
import munit.Assertions.assert

object EntityOps extends EntityOps
trait EntityOps:

  extension (entity: EntityDocument)

    def noneScore: EntityDocument = entity match {
      case e: Project => e.copy(score = None)
      case e: User    => e.copy(score = None)
    }

    def assertVersionNot(v: Version): EntityDocument =
      assert(entity.version != v)
      entity

    def setVersion(v: Version): EntityDocument = entity match {
      case e: Project => e.copy(version = v)
      case e: User    => e.copy(version = v)
    }
