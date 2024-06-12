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
  val projectDescGen: Gen[Description] =
    alphaStringGen(max = 30).map(Description.apply)

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
    Gen.oneOf(MemberRole.valuesV2)

  val visibilityGen: Gen[Visibility] =
    Gen.oneOf(Visibility.values.toList)
  val creationDateGen: Gen[CreationDate] =
    instantGen().map(CreationDate.apply)

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

  val userFirstNameGen: Gen[FirstName] = Gen
    .oneOf("Eike", "Kuba", "Ralf", "Lorenzo", "Jean-Pierre", "Alfonso")
    .map(FirstName.apply)
  val userLastNameGen: Gen[LastName] = Gen
    .oneOf("Kowalski", "Doe", "Tourist", "Milkman", "Da Silva", "Bar")
    .map(LastName.apply)
  def userEmailGen(first: FirstName, last: LastName): Gen[Email] = Gen
    .oneOf("mail.com", "hotmail.com", "epfl.ch", "ethz.ch")
    .map(v => Email(s"$first.$last@$v"))
  val userEmailGen: Gen[Email] =
    (
      Gen.oneOf("mail.com", "hotmail.com", "epfl.ch", "ethz.ch"),
      userFirstNameGen,
      userLastNameGen
    ).mapN((f, l, p) => Email(s"$f.$l@$p"))

  val groupNameGen: Gen[Name] =
    Gen.oneOf(
      List("sdsc", "renku", "datascience", "rocket-science").map(Name.apply)
    )
  val groupDescGen: Gen[Description] =
    alphaStringGen(max = 5).map(Description.apply)
