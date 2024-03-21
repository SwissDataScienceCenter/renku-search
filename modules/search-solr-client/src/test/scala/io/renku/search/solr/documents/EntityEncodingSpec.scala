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

import io.bullet.borer.Json
import io.renku.search.GeneratorSyntax.*
import io.renku.search.solr.client.SolrDocumentGenerators
import munit.FunSuite

class EntityEncodingSpec extends FunSuite:

  test("full project with kind and type"):
    val project = SolrDocumentGenerators.projectDocumentGen.generateOne
    val pJson = Json.encode(project).toUtf8String
    val p = Json.decode(pJson.getBytes).to[EntityDocument].value
    assert(pJson.contains(""""_kind":"fullentity""""))
    assert(pJson.contains(""""_type":"Project""""))
    assertEquals(p, project)
    val projectADT: EntityDocument = project
    assertEquals(Json.encode(projectADT).toUtf8String, pJson)

  test("encode partial project with kind and type"):
    val partial = SolrDocumentGenerators.partialProjectGen.generateOne
    val pJson = Json.encode(partial).toUtf8String
    val p = Json.decode(pJson.getBytes).to[PartialEntityDocument].value
    assert(pJson.contains(""""_kind":"partialentity""""))
    assert(pJson.contains(""""_type":"Project""""))
    assertEquals(p, partial)
    val projectADT: PartialEntityDocument = partial
    assertEquals(Json.encode(projectADT).toUtf8String, pJson)

  test("encode user with kind, type and visibility"):
    val user = SolrDocumentGenerators.userDocumentGen.generateOne
    val uJson = Json.encode(user).toUtf8String
    val u = Json.decode(uJson.getBytes).to[EntityDocument].value
    assert(uJson.contains(""""_kind":"fullentity""""))
    assert(uJson.contains(""""_type":"User""""))
    assert(uJson.contains(""""visibility":"public""""))
    assertEquals(u, user)
    val userADT: EntityDocument = user
    assertEquals(Json.encode(userADT).toUtf8String, uJson)
