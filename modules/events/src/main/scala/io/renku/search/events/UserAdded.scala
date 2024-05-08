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
import io.renku.search.model.{Id, Namespace, users}
import org.apache.avro.Schema

sealed trait UserAdded extends RenkuEventPayload:
  def fold[A](fv1: v1.UserAdded => A, fv2: v2.UserAdded => A): A
  def withId(id: Id): UserAdded
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v1.UserAdded.SCHEMA$, _ => v2.UserAdded.SCHEMA$)

object UserAdded:
  def apply(
      id: Id,
      namespace: Namespace,
      firstName: Option[users.FirstName],
      lastName: Option[users.LastName],
      email: Option[users.Email]
  ): UserAdded =
    V2(
      v2.UserAdded(
        id.value,
        firstName.map(_.value),
        lastName.map(_.value),
        email.map(_.value),
        namespace.value
      )
    )

  final case class V1(event: v1.UserAdded) extends UserAdded:
    val id: Id = Id(event.id)
    def withId(id: Id): UserAdded = V1(event.copy(id = id.value))
    def fold[A](fv1: v1.UserAdded => A, fv2: v2.UserAdded => A): A = fv1(event)
    val namespace: Option[Namespace] = None

  final case class V2(event: v2.UserAdded) extends UserAdded:
    val id: Id = Id(event.id)
    def withId(id: Id): UserAdded = V2(event.copy(id = id.value))
    def fold[A](fv1: v1.UserAdded => A, fv2: v2.UserAdded => A): A = fv2(event)
    val namespace: Option[Namespace] = Some(Namespace(event.namespace))

  given AvroEncoder[UserAdded] =
    val v1e = AvroEncoder[v1.UserAdded]
    val v2e = AvroEncoder[v2.UserAdded]
    AvroEncoder.basic { v =>
      v.fold(v1e.encode(v.schema), v2e.encode(v.schema))
    }

  given EventMessageDecoder[UserAdded] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.UserAdded.SCHEMA$
          qm.toMessage[v1.UserAdded](schema)
            .map(_.map(UserAdded.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.UserAdded.SCHEMA$
          qm.toMessage[v2.UserAdded](schema)
            .map(_.map(UserAdded.V2.apply))
    }

  given Show[UserAdded] = Show.show(_.fold(_.toString, _.toString))
