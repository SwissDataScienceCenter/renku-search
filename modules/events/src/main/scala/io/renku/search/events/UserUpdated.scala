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

import cats.data.NonEmptyList
import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.{v1, v2}
import io.renku.search.model.Id
import org.apache.avro.Schema
import cats.Show

sealed trait UserUpdated extends RenkuEventPayload:
  def fold[A](fv1: v1.UserUpdated => A, fv2: v2.UserUpdated => A): A
  def withId(id: Id): UserUpdated
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v1.UserUpdated.SCHEMA$, _ => v2.UserUpdated.SCHEMA$)

object UserUpdated:
  final case class V1(event: v1.UserUpdated) extends UserUpdated:
    val id: Id = Id(event.id)
    def withId(id: Id): UserUpdated = V1(event.copy(id = id.value))
    def fold[A](fv1: v1.UserUpdated => A, fv2: v2.UserUpdated => A): A = fv1(event)

  final case class V2(event: v2.UserUpdated) extends UserUpdated:
    val id: Id = Id(event.id)
    def withId(id: Id): UserUpdated = V2(event.copy(id = id.value))
    def fold[A](fv1: v1.UserUpdated => A, fv2: v2.UserUpdated => A): A = fv2(event)

  given AvroEncoder[UserUpdated] =
    val v1e = AvroEncoder[v1.UserUpdated]
    val v2e = AvroEncoder[v2.UserUpdated]
    AvroEncoder.basic { v =>
      v.fold(v1e.encode(v.schema), v2e.encode(v.schema))
    }

  given EventMessageDecoder[UserUpdated] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.UserUpdated.SCHEMA$
          qm.toMessage[v1.UserUpdated](schema)
            .map(_.map(UserUpdated.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.UserUpdated.SCHEMA$
          qm.toMessage[v2.UserUpdated](schema)
            .map(_.map(UserUpdated.V2.apply))
    }

  given Show[UserUpdated] = Show.show(_.fold(_.toString, _.toString))
