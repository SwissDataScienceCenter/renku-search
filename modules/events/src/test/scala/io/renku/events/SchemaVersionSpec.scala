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

package io.renku.events

import io.renku.search.events.SchemaVersion
import munit.FunSuite

class SchemaVersionSpec extends FunSuite:

  test("parse values successfully"):
    val v1Values = List("v1", "V1", "1")
    v1Values.foreach { str =>
      assertEquals(SchemaVersion.fromString(str), Right(SchemaVersion.V1))
    }

    val v2Values = List("v2", "V2", "2")
    v2Values.foreach { str =>
      assertEquals(SchemaVersion.fromString(str), Right(SchemaVersion.V2))
    }
