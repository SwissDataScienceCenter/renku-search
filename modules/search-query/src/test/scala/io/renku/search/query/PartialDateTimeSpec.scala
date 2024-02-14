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

package io.renku.search.query

import munit.FunSuite

import java.time.{Instant, ZoneOffset}

class PartialDateTimeSpec extends FunSuite {
  val utc = ZoneOffset.UTC

  test("minimum") {
    assertEquals(
      PartialDateTime.unsafeFromString("2023-01").instantMin(utc),
      Instant.parse("2023-01-01T00:00:00Z")
    )
  }

  test("leap year") {
    assertEquals(
      PartialDateTime
        .unsafeFromString("2024-02")
        .instantMax(utc),
      Instant.parse("2024-02-29T23:59:59Z")
    )
    assertEquals(
      PartialDateTime
        .unsafeFromString("2023-02")
        .instantMax(utc),
      Instant.parse("2023-02-28T23:59:59Z")
    )
  }
}
