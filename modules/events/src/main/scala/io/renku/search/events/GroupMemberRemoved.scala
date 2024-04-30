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
import org.apache.avro.Schema

sealed trait GroupMemberRemoved extends RenkuEventPayload:
  def fold[A](fv2: v2.GroupMemberRemoved => A): A
  def withId(id: Id): GroupMemberRemoved
  def version: NonEmptyList[SchemaVersion] = NonEmptyList.of(SchemaVersion.V2)
  def schema: Schema = v2.GroupMemberRemoved.SCHEMA$
  def userId: Id = fold(a => Id(a.userId))

object GroupMemberRemoved:
  def apply(groupId: Id, userId: Id): GroupMemberRemoved =
    V2(v2.GroupMemberRemoved(groupId.value, userId.value))

  final case class V2(event: v2.GroupMemberRemoved) extends GroupMemberRemoved:
    lazy val id: Id = Id(event.groupId)
    def withId(id: Id): GroupMemberRemoved = V2(event.copy(groupId = id.value))
    def fold[A](fv2: v2.GroupMemberRemoved => A): A = fv2(event)

  given AvroEncoder[GroupMemberRemoved] =
    val v2e = AvroEncoder[v2.GroupMemberRemoved]
    AvroEncoder.basic { v =>
      v.fold(v2e.encode(v.schema))
    }

  given EventMessageDecoder[GroupMemberRemoved] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          Left(DecodeFailure.VersionNotSupported(qm.id, qm.header))

        case SchemaVersion.V2 =>
          val schema = v2.GroupMemberRemoved.SCHEMA$
          qm.toMessage[v2.GroupMemberRemoved](schema)
            .map(_.map(GroupMemberRemoved.V2.apply))
    }

  given Show[GroupMemberRemoved] =
    Show.show(_.fold(_.toString))
