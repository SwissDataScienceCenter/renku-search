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

final case class UserRemoved(id: Id) extends RenkuEventPayload:
  def withId(id: Id): UserRemoved = copy(id = id)
  def version: NonEmptyList[SchemaVersion] = SchemaVersion.all
  val schema: Schema = v2.UserRemoved.SCHEMA$

object UserRemoved:

  given AvroEncoder[UserRemoved] =
    val v2e = AvroEncoder[v2.UserRemoved]
    AvroEncoder.basic { v =>
      val event = v2.UserRemoved(v.id.value)
      v2e.encode(v.schema)(event)
    }

  given EventMessageDecoder[UserRemoved] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.UserRemoved.SCHEMA$
          qm.toMessage[v1.UserRemoved](schema)
            .map(_.map(e => UserRemoved(Id(e.id))))

        case SchemaVersion.V2 =>
          val schema = v2.UserRemoved.SCHEMA$
          qm.toMessage[v2.UserRemoved](schema)
            .map(_.map(e => UserRemoved(Id(e.id))))
    }
