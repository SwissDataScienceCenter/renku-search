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
import io.renku.search.query.{Query, QueryGenerators}
import munit.{FunSuite, ScalaCheckSuite}
import org.scalacheck.Prop

class QueryJsonSpec extends ScalaCheckSuite {

  property("query json encode/decode") {
    Prop.forAll(QueryGenerators.query) { q =>
      val jsonStr = Json.encode(q).toUtf8String
      val decoded = Json.decode(jsonStr.getBytes).to[Query].value
      assertEquals(decoded, q)
    }
  }
}
