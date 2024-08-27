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

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.v2
import io.renku.search.model.Id
import io.renku.search.model.MemberRole
import org.apache.avro.Schema

sealed trait GroupMemberAdded extends RenkuEventPayload:
  def fold[A](fv2: v2.GroupMemberAdded => A): A
  def withId(id: Id): GroupMemberAdded
  def withRole(role: MemberRole): GroupMemberAdded
  def version: NonEmptyList[SchemaVersion] = NonEmptyList.of(SchemaVersion.V2)
  def schema: Schema = v2.GroupMemberAdded.SCHEMA$
  def userId: Id = fold(a => Id(a.userId))
  def role: MemberRole
  val msgType: MsgType = MsgType.GroupMemberAdded

object GroupMemberAdded:
  def apply(groupId: Id, userId: Id, role: MemberRole): GroupMemberAdded =
    V2(v2.GroupMemberAdded(groupId.value, userId.value, v2.MemberRole.VIEWER))
      .withRole(role)

  final case class V2(event: v2.GroupMemberAdded) extends GroupMemberAdded:
    lazy val id: Id = Id(event.groupId)
    def withId(id: Id): GroupMemberAdded = V2(event.copy(groupId = id.value))
    def withRole(role: MemberRole): GroupMemberAdded =
      role match
        case MemberRole.Member => V2(event.copy(role = v2.MemberRole.VIEWER))
        case MemberRole.Viewer => V2(event.copy(role = v2.MemberRole.VIEWER))
        case MemberRole.Editor => V2(event.copy(role = v2.MemberRole.EDITOR))
        case MemberRole.Owner  => V2(event.copy(role = v2.MemberRole.OWNER))
    def role: MemberRole = event.role match
      case v2.MemberRole.OWNER  => MemberRole.Owner
      case v2.MemberRole.EDITOR => MemberRole.Editor
      case v2.MemberRole.VIEWER => MemberRole.Viewer
    def fold[A](fv2: v2.GroupMemberAdded => A): A = fv2(event)

  given AvroEncoder[GroupMemberAdded] =
    val v2e = AvroEncoder[v2.GroupMemberAdded]
    AvroEncoder.basic { v =>
      v.fold(v2e.encode(v.schema))
    }

  given EventMessageDecoder[GroupMemberAdded] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          Left(DecodeFailure.VersionNotSupported(qm.id, qm.header))

        case SchemaVersion.V2 =>
          val schema = v2.GroupMemberAdded.SCHEMA$
          qm.toMessage[v2.GroupMemberAdded](schema)
            .map(_.map(GroupMemberAdded.V2.apply))
    }

  given Show[GroupMemberAdded] =
    Show.show(_.fold(_.toString))
