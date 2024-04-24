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

sealed trait ProjectMemberUpdated extends RenkuEventPayload:
  def fold[A](
      fv1: v1.ProjectAuthorizationUpdated => A,
      fv2: v2.ProjectMemberUpdated => A
  ): A
  def withId(id: Id): ProjectMemberUpdated
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(
      _ => v1.ProjectAuthorizationUpdated.SCHEMA$,
      _ => v2.ProjectMemberUpdated.SCHEMA$
    )

object ProjectMemberUpdated:

  final case class V1(event: v1.ProjectAuthorizationUpdated) extends ProjectMemberUpdated:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberUpdated = V1(event.copy(projectId = id.value))
    def fold[A](
        fv1: v1.ProjectAuthorizationUpdated => A,
        fv2: v2.ProjectMemberUpdated => A
    ): A = fv1(event)

  final case class V2(event: v2.ProjectMemberUpdated) extends ProjectMemberUpdated:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberUpdated = V2(event.copy(projectId = id.value))
    def fold[A](
        fv1: v1.ProjectAuthorizationUpdated => A,
        fv2: v2.ProjectMemberUpdated => A
    ): A = fv2(event)

  given AvroEncoder[ProjectMemberUpdated] =
    val v1e = AvroEncoder[v1.ProjectAuthorizationUpdated]
    val v2e = AvroEncoder[v2.ProjectMemberUpdated]
    AvroEncoder { (schema, v) =>
      v.fold(a => v1e.encode(schema)(a), b => v2e.encode(schema)(b))
    }

  given EventMessageDecoder[ProjectMemberUpdated] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.ProjectAuthorizationUpdated.SCHEMA$
          qm.toMessage[v1.ProjectAuthorizationUpdated](schema)
            .map(_.map(ProjectMemberUpdated.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.ProjectMemberUpdated.SCHEMA$
          qm.toMessage[v2.ProjectMemberUpdated](schema)
            .map(_.map(ProjectMemberUpdated.V2.apply))
    }