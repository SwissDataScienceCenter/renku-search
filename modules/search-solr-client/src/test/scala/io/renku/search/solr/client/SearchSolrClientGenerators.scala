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

package io.renku.search.solr.client

import io.renku.search.solr.documents.ProjectDocument
import org.scalacheck.Gen

object SearchSolrClientGenerators:

  def projectDocumentGen(name: String, desc: String): Gen[ProjectDocument] =
    Gen.uuid.map(uuid =>
      ProjectDocument(
        id = uuid.toString,
        name = "solr-project",
        description = "solr project description"
      )
    )

  extension [V](gen: Gen[V]) def generateOne: V = gen.sample.getOrElse(generateOne)
