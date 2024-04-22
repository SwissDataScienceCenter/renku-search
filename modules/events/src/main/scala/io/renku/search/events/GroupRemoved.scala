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
import io.renku.events.v2
import io.renku.search.model.Id
import org.apache.avro.Schema

sealed trait GroupRemoved extends RenkuEventPayload:
  def fold[A](fv2: v2.GroupRemoved => A): A
  def withId(id: Id): GroupRemoved
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v2.GroupRemoved.SCHEMA$)

object GroupRemoved:

  final case class V2(event: v2.GroupRemoved) extends GroupRemoved:
    val id: Id = Id(event.id)

    def withId(id: Id): GroupRemoved = V2(event.copy(id = id.value))

    def fold[A](fv2: v2.GroupRemoved => A): A = fv2(event)

  given (using v2e: AvroEncoder[v2.GroupRemoved]): AvroEncoder[GroupRemoved] =
    AvroEncoder { (schema, v) =>
      v.fold(b => v2e.encode(schema)(b))
    }
