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

package io.renku.search.query.json

import io.bullet.borer.Json
import io.renku.search.query.{PartialDateTime, Query}
import io.renku.search.query.Query.Segment
import munit.FunSuite

import java.time.Instant

class QueryJsonSpec extends FunSuite {

  test("playing") {
    println(Query.empty.asString)
    val q = Query(
      Segment.projectIdIs("p1"),
      Segment.text("foo bar"),
      Segment.nameIs("ai-project-15048"),
      Segment.creationDateLt(PartialDateTime.fromInstant(Instant.now()))
    )
    println(q.asString)
    val jsonStr = Json.encode(q).toUtf8String
    println(jsonStr)
    val decoded = Json.decode(jsonStr.getBytes).to[Query].value
    println(decoded)
    assertEquals(decoded, q)

    val q2 = Query(Segment.projectIdIs("id-2"), Segment.projectIdIs("id-3"))
    val q2Json = Json.encode(q2).toUtf8String
    assertEquals(q2Json, """{"projectId":"id-2","projectId":"id-3"}""")
    val decodedQ2 = Json.decode(q2Json.getBytes).to[Query].value
    println(decodedQ2)
  }
}
