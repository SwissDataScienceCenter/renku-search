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

import org.scalacheck.Gen
import org.scalacheck.Gen.{alphaChar, alphaNumChar}

import java.time.Instant
import java.time.temporal.ChronoUnit
import io.renku.search.events.{ProjectCreated, ProjectUpdated}
import io.renku.search.model.ModelGenerators

object EventsGenerators:

  val projectVisibilityGen: Gen[v1.Visibility] =
    Gen.oneOf(v1.Visibility.values().toList)

  val v2ProjectVisibilityGen: Gen[v2.Visibility] =
    Gen.oneOf(v2.Visibility.values().toList)

  val projectMemberRoleGen: Gen[v1.ProjectMemberRole] =
    Gen.oneOf(v1.ProjectMemberRole.values().toList)

  val v2ProjectMemberRoleGen: Gen[v2.MemberRole] =
    Gen.oneOf(v2.MemberRole.values().toList)

  def v1ProjectCreatedGen(prefix: String): Gen[v1.ProjectCreated] =
    for
      id <- Gen.uuid.map(_.toString)
      name <- stringGen(max = 5).map(v => s"$prefix-$v")
      repositoriesCount <- Gen.choose(1, 3)
      repositories <- Gen.listOfN(repositoriesCount, stringGen(10))
      visibility <- projectVisibilityGen
      maybeDesc <- Gen.option(stringGen(20))
      keywords <- ModelGenerators.keywordsGen
      creator <- Gen.uuid.map(_.toString)
    yield v1.ProjectCreated(
      id,
      name,
      name,
      repositories,
      visibility,
      maybeDesc,
      keywords.map(_.value),
      creator,
      Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )

  def v2ProjectCreatedGen(prefix: String): Gen[v2.ProjectCreated] =
    for
      id <- Gen.uuid.map(_.toString)
      name <- stringGen(max = 5).map(v => s"$prefix-$v")
      ns <- ModelGenerators.namespaceGen
      slug = s"${ns.value}/$name"
      repositoriesCount <- Gen.choose(1, 3)
      repositories <- Gen.listOfN(repositoriesCount, stringGen(10))
      visibility <- v2ProjectVisibilityGen
      maybeDesc <- Gen.option(stringGen(20))
      keywords <- ModelGenerators.keywordsGen
      creator <- Gen.uuid.map(_.toString)
    yield v2.ProjectCreated(
      id,
      name,
      ns.value,
      slug,
      repositories,
      visibility,
      maybeDesc,
      keywords.map(_.value),
      creator,
      Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )

  def projectCreatedGen(prefix: String): Gen[ProjectCreated] =
    Gen.oneOf(
      v1ProjectCreatedGen(prefix).map(ProjectCreated.V1.apply),
      v2ProjectCreatedGen(prefix).map(ProjectCreated.V2.apply)
    )

  def v1ProjectUpdatedGen(prefix: String): Gen[v1.ProjectUpdated] =
    for
      id <- Gen.uuid.map(_.toString)
      name <- stringGen(max = 5).map(v => s"$prefix-$v")
      repositoriesCount <- Gen.choose(1, 3)
      repositories <- Gen.listOfN(repositoriesCount, stringGen(10))
      visibility <- projectVisibilityGen
      maybeDesc <- Gen.option(stringGen(20))
      keywords <- ModelGenerators.keywordsGen
    yield v1.ProjectUpdated(
      id,
      name,
      name,
      repositories,
      visibility,
      maybeDesc,
      keywords.map(_.value)
    )

  def v2ProjectUpdatedGen(prefix: String): Gen[v2.ProjectUpdated] =
    for
      id <- Gen.uuid.map(_.toString)
      name <- stringGen(max = 5).map(v => s"$prefix-$v")
      ns <- ModelGenerators.namespaceGen
      slug = s"${ns.value}/$name"
      repositoriesCount <- Gen.choose(1, 3)
      repositories <- Gen.listOfN(repositoriesCount, stringGen(10))
      visibility <- v2ProjectVisibilityGen
      maybeDesc <- Gen.option(stringGen(20))
      keywords <- ModelGenerators.keywordsGen
    yield v2.ProjectUpdated(
      id,
      name,
      ns.value,
      slug,
      repositories,
      visibility,
      maybeDesc,
      keywords.map(_.value)
    )

  def projectUpdatedGen(prefix: String): Gen[ProjectUpdated] =
    Gen.oneOf(
      v1ProjectUpdatedGen(prefix).map(ProjectUpdated.V1.apply),
      v2ProjectUpdatedGen(prefix).map(ProjectUpdated.V2.apply)
    )

  def projectAuthorizationAddedGen(
      projectIdGen: Gen[String] = Gen.uuid.map(_.toString),
      roleGen: Gen[v1.ProjectMemberRole] = projectMemberRoleGen
  ): Gen[v1.ProjectAuthorizationAdded] =
    for
      projectId <- projectIdGen
      userId <- Gen.uuid.map(_.toString)
      role <- roleGen
    yield v1.ProjectAuthorizationAdded(projectId, userId, role)

  def projectAuthorizationUpdatedGen(
      projectIdGen: Gen[String] = Gen.uuid.map(_.toString),
      roleGen: Gen[v1.ProjectMemberRole] = projectMemberRoleGen
  ): Gen[v1.ProjectAuthorizationUpdated] =
    for
      projectId <- projectIdGen
      userId <- Gen.uuid.map(_.toString)
      role <- roleGen
    yield v1.ProjectAuthorizationUpdated(projectId, userId, role)

  def userAddedGen(prefix: String): Gen[v1.UserAdded] =
    for
      id <- Gen.uuid.map(_.toString)
      firstName <- Gen.option(alphaStringGen(max = 5).map(v => s"$prefix-$v"))
      lastName <- alphaStringGen(max = 5).map(v => s"$prefix-$v")
      email <- Gen.option(stringGen(max = 5).map(host => s"$lastName@$host.com"))
    yield v1.UserAdded(
      id,
      firstName,
      Some(lastName),
      email
    )

  def groupAddedGen(prefix: String): Gen[v2.GroupAdded] =
    for
      id <- Gen.uuid.map(_.toString)
      name <- alphaStringGen(max = 5).map(v => s"$prefix-$v")
      maybeDesc <- Gen.option(stringGen(20))
      namespace <- alphaStringGen(max = 5).map(v => s"$prefix-$v")
    yield v2.GroupAdded(id, name, maybeDesc, namespace)

  def stringGen(max: Int): Gen[String] =
    Gen
      .chooseNum(3, max)
      .flatMap(Gen.stringOfN(_, alphaNumChar))

  def alphaStringGen(max: Int): Gen[String] =
    Gen
      .chooseNum(3, max)
      .flatMap(Gen.stringOfN(_, alphaChar))
