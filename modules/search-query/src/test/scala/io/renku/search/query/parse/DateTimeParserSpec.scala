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

package io.renku.search.query.parse

import io.renku.search.query.{DateTimeCalc, PartialDateTime}
import munit.FunSuite

import java.time.{Instant, Period, ZoneOffset}
import java.time.temporal.ChronoUnit

class DateTimeParserSpec extends FunSuite with ParserSuite {
  val utc = ZoneOffset.UTC

  test("PartialDate: current time") {
    val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    assertEquals(
      DateTimeParser.partialDateTime.run(now.toString).instantMin(utc),
      now
    )
    assertEquals(
      DateTimeParser.partialDateTime.run(now.toString).instantMax(utc),
      now
    )
  }

  test("PartialDate: no time") {
    assertEquals(
      DateTimeParser.partialDateTime.run("2024-02"),
      PartialDateTime(PartialDateTime.Date(2024, Some(2)))
    )
    assertEquals(
      DateTimeParser.partialDateTime.run("2023-02-14"),
      PartialDateTime(PartialDateTime.Date(2023, Some(2), Some(14)))
    )
  }

  test("DateCalc") {
    val p = DateTimeParser.dateCalc
    assertEquals(
      p.run("2023-02-15-10d"),
      DateTimeCalc(
        PartialDateTime.unsafeFromString("2023-02-15"),
        Period.ofDays(-10),
        false
      )
    )
    assertEquals(
      p.run("2023-02+10d"),
      DateTimeCalc(
        PartialDateTime.unsafeFromString("2023-02"),
        Period.ofDays(10),
        false
      )
    )
    assertEquals(
      p.run("2023-02/10d"),
      DateTimeCalc(
        PartialDateTime.unsafeFromString("2023-02"),
        Period.ofDays(10),
        true
      )
    )
  }
}
