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

package io.renku.search.model

import io.renku.search.model.projects.*
import org.scalacheck.Gen

import java.time.Instant
import java.time.temporal.ChronoUnit

object ModelGenerators {

  val visibilityGen: Gen[Visibility] = Gen.oneOf(Visibility.values.toList)
  val creationDateGen: Gen[CreationDate] = instantGen().map(CreationDate.apply)

  private def instantGen(
      min: Instant = Instant.EPOCH,
      max: Instant = Instant.now()
  ): Gen[Instant] =
    Gen
      .chooseNum(min.toEpochMilli, max.toEpochMilli)
      .map(Instant.ofEpochMilli(_).truncatedTo(ChronoUnit.MILLIS))

}
