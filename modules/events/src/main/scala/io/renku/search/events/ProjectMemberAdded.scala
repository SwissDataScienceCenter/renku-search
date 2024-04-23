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
import org.apache.avro.Schema
import io.renku.search.model.Id
import cats.data.NonEmptyList

sealed trait ProjectMemberAdded extends RenkuEventPayload:
  def fold[A](fv1: v1.ProjectAuthorizationAdded => A, fv2: v2.ProjectMemberAdded => A): A
  def withId(id: Id): ProjectMemberAdded
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v1.ProjectAuthorizationAdded.SCHEMA$, _ => v2.ProjectMemberAdded.SCHEMA$)

object ProjectMemberAdded:

  final case class V1(event: v1.ProjectAuthorizationAdded) extends ProjectMemberAdded:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberAdded = V1(event.copy(projectId = id.value))
    def fold[A](
        fv1: v1.ProjectAuthorizationAdded => A,
        fv2: v2.ProjectMemberAdded => A
    ): A = fv1(event)

  final case class V2(event: v2.ProjectMemberAdded) extends ProjectMemberAdded:
    lazy val id: Id = Id(event.projectId)
    def withId(id: Id): ProjectMemberAdded = V2(event.copy(projectId = id.value))
    def fold[A](
        fv1: v1.ProjectAuthorizationAdded => A,
        fv2: v2.ProjectMemberAdded => A
    ): A = fv2(event)

  given (using
      v1e: AvroEncoder[v1.ProjectAuthorizationAdded],
      v2e: AvroEncoder[v2.ProjectMemberAdded]
  ): AvroEncoder[ProjectMemberAdded] =
    AvroEncoder { (schema, v) =>
      v.fold(a => v1e.encode(schema)(a), b => v2e.encode(schema)(b))
    }
