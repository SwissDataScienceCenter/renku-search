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

import cats.syntax.all.*
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.*

import java.time.Instant
import java.time.temporal.ChronoUnit

object ModelGenerators:

  val projectVisibilityGen: Gen[projects.Visibility] =
    Gen.oneOf(projects.Visibility.values.toList)
  val projectCreationDateGen: Gen[projects.CreationDate] =
    instantGen().map(projects.CreationDate.apply)

  private def instantGen(
      min: Instant = Instant.EPOCH,
      max: Instant = Instant.now()
  ): Gen[Instant] =
    Gen
      .chooseNum(min.toEpochMilli, max.toEpochMilli)
      .map(Instant.ofEpochMilli(_).truncatedTo(ChronoUnit.MILLIS))

  val userIdGen: Gen[users.Id] = Gen.uuid.map(uuid => users.Id(uuid.toString))
  val userFirstNameGen: Gen[users.FirstName] = Gen
    .oneOf("Eike", "Kuba", "Ralf", "Lorenzo", "Jean-Pierre", "Alfonso")
    .map(users.FirstName.apply)
  val userLastNameGen: Gen[users.LastName] = Gen
    .oneOf("Kowalski", "Doe", "Tourist", "Milkman", "Da Silva", "Bar")
    .map(users.LastName.apply)
  def userEmailGen(first: users.FirstName, last: users.LastName): Gen[users.Email] = Gen
    .oneOf("mail.com", "hotmail.com", "epfl.ch", "ethz.ch")
    .map(v => users.Email(s"$first.$last@$v"))
  val userEmailGen: Gen[users.Email] =
    (
      Gen.oneOf("mail.com", "hotmail.com", "epfl.ch", "ethz.ch"),
      userFirstNameGen,
      userLastNameGen
    ).mapN((f, l, p) => users.Email(s"$f.$l@$p"))
