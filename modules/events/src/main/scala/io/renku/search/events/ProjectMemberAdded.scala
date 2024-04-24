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

package io.renku.search.events

import io.renku.events.{v1, v2}
import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import org.apache.avro.Schema
import io.renku.search.model.Id
import cats.data.NonEmptyList
import cats.Show
import io.renku.search.model.MemberRole

sealed trait ProjectMemberAdded extends RenkuEventPayload:
  def fold[A](fv1: v1.ProjectAuthorizationAdded => A, fv2: v2.ProjectMemberAdded => A): A
  def withId(id: Id): ProjectMemberAdded
  def withRole(role: MemberRole): ProjectMemberAdded
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v1.ProjectAuthorizationAdded.SCHEMA$, _ => v2.ProjectMemberAdded.SCHEMA$)

object ProjectMemberAdded:

  final case class V1(event: v1.ProjectAuthorizationAdded) extends ProjectMemberAdded:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberAdded = V1(event.copy(projectId = id.value))
    def withRole(role: MemberRole): ProjectMemberAdded =
      role match
        case MemberRole.Member => V1(event.copy(role = v1.ProjectMemberRole.MEMBER))
        case MemberRole.Viewer => V1(event.copy(role = v1.ProjectMemberRole.MEMBER))
        case MemberRole.Editor => V1(event.copy(role = v1.ProjectMemberRole.MEMBER))
        case MemberRole.Owner  => V1(event.copy(role = v1.ProjectMemberRole.OWNER))

    def fold[A](
        fv1: v1.ProjectAuthorizationAdded => A,
        fv2: v2.ProjectMemberAdded => A
    ): A = fv1(event)

  final case class V2(event: v2.ProjectMemberAdded) extends ProjectMemberAdded:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberAdded = V2(event.copy(projectId = id.value))
    def withRole(role: MemberRole): ProjectMemberAdded =
      role match
        case MemberRole.Member => V2(event.copy(role = v2.MemberRole.VIEWER))
        case MemberRole.Viewer => V2(event.copy(role = v2.MemberRole.VIEWER))
        case MemberRole.Editor => V2(event.copy(role = v2.MemberRole.EDITOR))
        case MemberRole.Owner  => V2(event.copy(role = v2.MemberRole.OWNER))

    def fold[A](
        fv1: v1.ProjectAuthorizationAdded => A,
        fv2: v2.ProjectMemberAdded => A
    ): A = fv2(event)

  given AvroEncoder[ProjectMemberAdded] =
    val v1e = AvroEncoder[v1.ProjectAuthorizationAdded]
    val v2e = AvroEncoder[v2.ProjectMemberAdded]
    AvroEncoder { (schema, v) =>
      v.fold(a => v1e.encode(schema)(a), b => v2e.encode(schema)(b))
    }

  given EventMessageDecoder[ProjectMemberAdded] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.ProjectAuthorizationAdded.SCHEMA$
          qm.toMessage[v1.ProjectAuthorizationAdded](schema)
            .map(_.map(ProjectMemberAdded.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.ProjectMemberAdded.SCHEMA$
          qm.toMessage[v2.ProjectMemberAdded](schema)
            .map(_.map(ProjectMemberAdded.V2.apply))
    }

  given Show[ProjectMemberAdded] =
    Show.show(_.fold(_.toString, _.toString))
