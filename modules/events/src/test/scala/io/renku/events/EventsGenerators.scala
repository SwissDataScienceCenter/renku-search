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

import io.renku.events.v1.{ProjectCreated, UserAdded, Visibility}
import org.scalacheck.Gen
import org.scalacheck.Gen.alphaNumChar

import java.time.Instant
import java.time.temporal.ChronoUnit

object EventsGenerators:

  def projectCreatedGen(prefix: String): Gen[ProjectCreated] =
    for
      id <- Gen.uuid.map(_.toString)
      name <- stringGen(max = 5).map(v => s"$prefix-$v")
      repositories <- Gen.listOfN(Gen.choose(1, 3).generateOne, stringGen(10))
      visibility <- Gen.oneOf(Visibility.values().toList)
      maybeDesc <- Gen.option(stringGen(20))
      creator <- Gen.uuid.map(_.toString)
    yield ProjectCreated(
      id,
      name,
      name,
      repositories,
      visibility,
      maybeDesc,
      creator,
      Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )

  def userAddedGen(prefix: String): Gen[UserAdded] =
    for
      id <- Gen.uuid.map(_.toString)
      firstName <- Gen.option(stringGen(max = 5).map(v => s"$prefix-$v"))
      lastName <- stringGen(max = 5).map(v => s"$prefix-$v")
      email <- Gen.option(stringGen(max = 5).map(host => s"$lastName@$host.com"))
    yield UserAdded(
      id,
      firstName,
      Some(lastName),
      email
    )

  def stringGen(max: Int): Gen[String] =
    Gen
      .chooseNum(3, max)
      .flatMap(Gen.stringOfN(_, alphaNumChar))

  extension [V](gen: Gen[V]) def generateOne: V = gen.sample.getOrElse(generateOne)
