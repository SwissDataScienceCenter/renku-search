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

import io.renku.events.v1.*
import org.scalacheck.Gen
import org.scalacheck.Gen.{alphaChar, alphaNumChar}

import java.time.Instant
import java.time.temporal.ChronoUnit

object EventsGenerators:

  val projectVisibilityGen: Gen[Visibility] =
    Gen.oneOf(Visibility.values().toList)
  val projectMemberRoleGen: Gen[ProjectMemberRole] =
    Gen.oneOf(ProjectMemberRole.values().toList)

  def projectCreatedGen(prefix: String): Gen[ProjectCreated] =
    for
      id <- Gen.uuid.map(_.toString)
      name <- stringGen(max = 5).map(v => s"$prefix-$v")
      repositoriesCount <- Gen.choose(1, 3)
      repositories <- Gen.listOfN(repositoriesCount, stringGen(10))
      visibility <- projectVisibilityGen
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

  def projectAuthorizationAddedGen(
      projectIdGen: Gen[String] = Gen.uuid.map(_.toString),
      roleGen: Gen[ProjectMemberRole] = projectMemberRoleGen
  ): Gen[ProjectAuthorizationAdded] =
    for
      projectId <- projectIdGen
      userId <- Gen.uuid.map(_.toString)
      role <- roleGen
    yield ProjectAuthorizationAdded(projectId, userId, role)

  def projectAuthorizationUpdatedGen(
      projectIdGen: Gen[String] = Gen.uuid.map(_.toString),
      roleGen: Gen[ProjectMemberRole] = projectMemberRoleGen
  ): Gen[ProjectAuthorizationUpdated] =
    for
      projectId <- projectIdGen
      userId <- Gen.uuid.map(_.toString)
      role <- roleGen
    yield ProjectAuthorizationUpdated(projectId, userId, role)

  def userAddedGen(prefix: String): Gen[UserAdded] =
    for
      id <- Gen.uuid.map(_.toString)
      firstName <- Gen.option(alphaStringGen(max = 5).map(v => s"$prefix-$v"))
      lastName <- alphaStringGen(max = 5).map(v => s"$prefix-$v")
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

  def alphaStringGen(max: Int): Gen[String] =
    Gen
      .chooseNum(3, max)
      .flatMap(Gen.stringOfN(_, alphaChar))
