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
import io.renku.events.{v1, v2}
import io.renku.search.model.Id
import org.apache.avro.Schema

sealed trait ProjectMemberRemoved extends RenkuEventPayload:
  def fold[A](
      fv1: v1.ProjectAuthorizationRemoved => A,
      fv2: v2.ProjectMemberRemoved => A
  ): A
  def withId(id: Id): ProjectMemberRemoved
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(
      _ => v1.ProjectAuthorizationRemoved.SCHEMA$,
      _ => v2.ProjectMemberRemoved.SCHEMA$
    )
  def userId: Id = fold(a => Id(a.userId), b => Id(b.userId))

object ProjectMemberRemoved:
  def apply(projectId: Id, userId: Id): ProjectMemberRemoved =
    V2(v2.ProjectMemberRemoved(projectId.value, userId.value))

  final case class V1(event: v1.ProjectAuthorizationRemoved) extends ProjectMemberRemoved:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberRemoved = V1(event.copy(projectId = id.value))
    def fold[A](
        fv1: v1.ProjectAuthorizationRemoved => A,
        fv2: v2.ProjectMemberRemoved => A
    ): A = fv1(event)

  final case class V2(event: v2.ProjectMemberRemoved) extends ProjectMemberRemoved:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberRemoved = V2(event.copy(projectId = id.value))
    def fold[A](
        fv1: v1.ProjectAuthorizationRemoved => A,
        fv2: v2.ProjectMemberRemoved => A
    ): A = fv2(event)

  given AvroEncoder[ProjectMemberRemoved] =
    val v1e = AvroEncoder[v1.ProjectAuthorizationRemoved]
    val v2e = AvroEncoder[v2.ProjectMemberRemoved]
    AvroEncoder.basic { v =>
      v.fold(v1e.encode(v.schema), v2e.encode(v.schema))
    }

  given EventMessageDecoder[ProjectMemberRemoved] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.ProjectAuthorizationRemoved.SCHEMA$
          qm.toMessage[v1.ProjectAuthorizationRemoved](schema)
            .map(_.map(ProjectMemberRemoved.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.ProjectMemberRemoved.SCHEMA$
          qm.toMessage[v2.ProjectMemberRemoved](schema)
            .map(_.map(ProjectMemberRemoved.V2.apply))
    }

  given Show[ProjectMemberRemoved] =
    Show.show(_.fold(_.toString, _.toString))
  given MsgType.Mapping[ProjectMemberRemoved] =
    MsgType.Mapping.of(MsgType.ProjectMemberRemoved)
