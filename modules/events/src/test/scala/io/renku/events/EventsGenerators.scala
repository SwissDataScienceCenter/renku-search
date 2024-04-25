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

import java.time.Instant
import java.time.temporal.ChronoUnit

import io.renku.events.v1.ProjectAuthorizationAdded
import io.renku.search.events.*
import io.renku.search.model.Id
import io.renku.search.model.MemberRole
import io.renku.search.model.ModelGenerators
import org.apache.avro.Schema
import org.scalacheck.Gen
import org.scalacheck.Gen.{alphaChar, alphaNumChar}

object EventsGenerators:

  val projectVisibilityGen: Gen[v1.Visibility] =
    Gen.oneOf(v1.Visibility.values().toList)

  val v2ProjectVisibilityGen: Gen[v2.Visibility] =
    Gen.oneOf(v2.Visibility.values().toList)

  val projectMemberRoleGen: Gen[v1.ProjectMemberRole] =
    Gen.oneOf(v1.ProjectMemberRole.values().toList)

  val v2ProjectMemberRoleGen: Gen[v2.MemberRole] =
    Gen.oneOf(v2.MemberRole.values().toList)

  def messageIdGen: Gen[MessageId] =
    Gen.uuid.map(uid => MessageId(uid.toString))

  def messageSourceGen: Gen[MessageSource] =
    stringGen(max = 6).map(n => MessageSource(s"ms-$n"))

  def dataContentTypeGen: Gen[DataContentType] =
    Gen.oneOf(DataContentType.values.toSeq)

  def schemaVersionGen: Gen[SchemaVersion] =
    Gen.oneOf(SchemaVersion.all.toList)

  val requestIdGen: Gen[RequestId] = Gen.uuid.map(_.toString).map(RequestId(_))

  def messageHeaderGen: Gen[MessageHeader] =
    for
      src <- messageSourceGen
      dt <- dataContentTypeGen
      sv <- schemaVersionGen
      ts <- ModelGenerators.timestampGen
      req <- requestIdGen
    yield MessageHeader(src, dt, sv, ts, req)

  def eventMessageGen[A](schema: Schema, plgen: Gen[Seq[A]]): Gen[EventMessage[A]] =
    for
      id <- messageIdGen
      mh <- messageHeaderGen
      pl <- plgen
    yield EventMessage(id, mh, schema, pl)

  def eventMessageGen[A <: RenkuEventPayload](plgen: Gen[A]): Gen[EventMessage[A]] =
    Gen.listOfN(1, plgen).flatMap { pl =>
      val schema = pl.head.schema
      eventMessageGen(schema, Gen.const(pl)).map(msg =>
        msg.copy(header = msg.header.withSchemaVersion(pl.head.version.head))
      )
    }

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

  def v1ProjectAuthorizationAddedGen(
      projectIdGen: Gen[String] = Gen.uuid.map(_.toString),
      roleGen: Gen[v1.ProjectMemberRole] = projectMemberRoleGen
  ): Gen[v1.ProjectAuthorizationAdded] =
    for
      projectId <- projectIdGen
      userId <- Gen.uuid.map(_.toString)
      role <- roleGen
    yield v1.ProjectAuthorizationAdded(projectId, userId, role)

  def v2ProjectMemberAddedGen(
      projectIdGen: Gen[String] = Gen.uuid.map(_.toString),
      roleGen: Gen[v2.MemberRole] = v2ProjectMemberRoleGen
  ): Gen[v2.ProjectMemberAdded] =
    for
      projectId <- projectIdGen
      userId <- Gen.uuid.map(_.toString)
      role <- roleGen
    yield v2.ProjectMemberAdded(projectId, userId, role)

  def projectMemberAddedGen: Gen[ProjectMemberAdded] =
    Gen.oneOf(
      v1ProjectAuthorizationAddedGen().map(ProjectMemberAdded.V1.apply),
      v2ProjectMemberAddedGen().map(ProjectMemberAdded.V2.apply)
    )

  def projectMemberAdded(
      projectId: Id,
      userId: Id,
      role: MemberRole
  ): Gen[ProjectMemberAdded] =
    role match
      case MemberRole.Member =>
        Gen.const(
          ProjectMemberAdded.V1(
            v1.ProjectAuthorizationAdded(
              projectId.value,
              userId.value,
              v1.ProjectMemberRole.MEMBER
            )
          )
        )
      case MemberRole.Viewer =>
        Gen.const(
          ProjectMemberAdded.V2(
            v2.ProjectMemberAdded(projectId.value, userId.value, v2.MemberRole.VIEWER)
          )
        )
      case MemberRole.Editor =>
        Gen.const(
          ProjectMemberAdded.V2(
            v2.ProjectMemberAdded(projectId.value, userId.value, v2.MemberRole.EDITOR)
          )
        )
      case MemberRole.Owner =>
        Gen.const(
          ProjectMemberAdded.V2(
            v2.ProjectMemberAdded(projectId.value, userId.value, v2.MemberRole.OWNER)
          )
        )

  def v1ProjectAuthorizationUpdatedGen(
      projectIdGen: Gen[String] = Gen.uuid.map(_.toString),
      roleGen: Gen[v1.ProjectMemberRole] = projectMemberRoleGen
  ): Gen[v1.ProjectAuthorizationUpdated] =
    for
      projectId <- projectIdGen
      userId <- Gen.uuid.map(_.toString)
      role <- roleGen
    yield v1.ProjectAuthorizationUpdated(projectId, userId, role)

  def v2ProjectMemberUpdatedGen(
      projectIdGen: Gen[String] = Gen.uuid.map(_.toString),
      roleGen: Gen[v2.MemberRole] = v2ProjectMemberRoleGen
  ): Gen[v2.ProjectMemberUpdated] =
    for
      projectId <- projectIdGen
      userId <- Gen.uuid.map(_.toString)
      role <- roleGen
    yield v2.ProjectMemberUpdated(projectId, userId, role)

  def projectMemberUpdatedGen: Gen[ProjectMemberUpdated] =
    Gen.oneOf(
      v1ProjectAuthorizationUpdatedGen().map(ProjectMemberUpdated.V1.apply),
      v2ProjectMemberUpdatedGen().map(ProjectMemberUpdated.V2.apply)
    )

  def projectMemberUpdated(
      projectId: Id,
      userId: Id,
      role: MemberRole
  ): Gen[ProjectMemberUpdated] =
    role match
      case MemberRole.Member =>
        Gen.const(
          ProjectMemberUpdated.V1(
            v1.ProjectAuthorizationUpdated(
              projectId.value,
              userId.value,
              v1.ProjectMemberRole.MEMBER
            )
          )
        )
      case MemberRole.Viewer =>
        Gen.const(
          ProjectMemberUpdated.V2(
            v2.ProjectMemberUpdated(projectId.value, userId.value, v2.MemberRole.VIEWER)
          )
        )
      case MemberRole.Editor =>
        Gen.const(
          ProjectMemberUpdated.V2(
            v2.ProjectMemberUpdated(projectId.value, userId.value, v2.MemberRole.EDITOR)
          )
        )
      case MemberRole.Owner =>
        Gen.const(
          ProjectMemberUpdated.V2(
            v2.ProjectMemberUpdated(projectId.value, userId.value, v2.MemberRole.OWNER)
          )
        )

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

  def v2GroupAddedGen(prefix: String): Gen[v2.GroupAdded] =
    for
      id <- Gen.uuid.map(_.toString)
      name <- alphaStringGen(max = 5).map(v => s"$prefix-$v")
      maybeDesc <- Gen.option(stringGen(20))
      namespace <- alphaStringGen(max = 5).map(v => s"$prefix-$v")
    yield v2.GroupAdded(id, name, maybeDesc, namespace)

  def groupAddedGen(prefix: String): Gen[GroupAdded] =
    v2GroupAddedGen(prefix).map(GroupAdded.V2.apply)

  def v2GroupUpdatedGen(prefix: String): Gen[v2.GroupUpdated] =
    for
      id <- Gen.uuid.map(_.toString)
      name <- alphaStringGen(max = 5).map(v => s"$prefix-$v")
      maybeDesc <- Gen.option(stringGen(20))
      namespace <- alphaStringGen(max = 5).map(v => s"$prefix-$v")
    yield v2.GroupUpdated(id, name, maybeDesc, namespace)

  def groupUpdatedGen(prefix: String): Gen[GroupUpdated] =
    v2GroupUpdatedGen(prefix).map(GroupUpdated.V2.apply)

  def stringGen(max: Int): Gen[String] =
    Gen
      .chooseNum(3, max)
      .flatMap(Gen.stringOfN(_, alphaNumChar))

  def alphaStringGen(max: Int): Gen[String] =
    Gen
      .chooseNum(3, max)
      .flatMap(Gen.stringOfN(_, alphaChar))
