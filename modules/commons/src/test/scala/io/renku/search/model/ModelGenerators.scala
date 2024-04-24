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

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.syntax.all.*

import org.scalacheck.Gen
import org.scalacheck.cats.implicits.*

object ModelGenerators:
  val idGen: Gen[Id] = Gen.uuid.map(uuid => Id(uuid.toString))
  val nameGen: Gen[Name] =
    alphaStringGen(max = 10).map(Name.apply)
  val projectDescGen: Gen[projects.Description] =
    alphaStringGen(max = 30).map(projects.Description.apply)

  val timestampGen: Gen[Timestamp] =
    Gen
      .choose(
        Instant.parse("2020-01-01T01:00:00Z").toEpochMilli,
        Instant.now().toEpochMilli
      )
      .map(millis => Timestamp(Instant.ofEpochMilli(millis)))

  val namespaceGen: Gen[Namespace] =
    alphaStringGen(max = 10).map(Namespace.apply)
  val memberRoleGen: Gen[MemberRole] =
    Gen.oneOf(MemberRole.values.toList)

  val projectVisibilityGen: Gen[projects.Visibility] =
    Gen.oneOf(projects.Visibility.values.toList)
  val projectCreationDateGen: Gen[projects.CreationDate] =
    instantGen().map(projects.CreationDate.apply)

  val keywordGen: Gen[Keyword] =
    Gen
      .oneOf("geo", "science", "fs24", "test", "music", "ml", "ai", "flights", "scala")
      .map(Keyword.apply)

  val keywordsGen: Gen[List[Keyword]] =
    Gen.choose(0, 4).flatMap(n => Gen.listOfN(n, keywordGen))

  private def alphaStringGen(max: Int): Gen[String] =
    Gen
      .chooseNum(3, max)
      .flatMap(Gen.stringOfN(_, Gen.alphaChar))

  private def instantGen(
      min: Instant = Instant.EPOCH,
      max: Instant = Instant.now()
  ): Gen[Instant] =
    Gen
      .chooseNum(min.toEpochMilli, max.toEpochMilli)
      .map(Instant.ofEpochMilli(_).truncatedTo(ChronoUnit.MILLIS))

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

  val groupNameGen: Gen[groups.Name] =
    Gen.oneOf(
      List("sdsc", "renku", "datascience", "rocket-science").map(groups.Name.apply)
    )
  val groupDescGen: Gen[groups.Description] =
    alphaStringGen(max = 5).map(groups.Description.apply)
