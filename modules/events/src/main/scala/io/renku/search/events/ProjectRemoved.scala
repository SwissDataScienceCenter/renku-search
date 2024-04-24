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
import io.renku.events.{v1, v2}
import io.renku.search.model.Id
import io.renku.avro.codec.all.given
import io.renku.avro.codec.AvroEncoder
import cats.Show

final case class ProjectRemoved(id: Id) extends RenkuEventPayload:
  val version: NonEmptyList[SchemaVersion] = SchemaVersion.all

object ProjectRemoved:
  given Show[ProjectRemoved] = Show.fromToString

  given AvroEncoder[ProjectRemoved] =
    val v2e = AvroEncoder[v2.ProjectRemoved]
    AvroEncoder { (_, v) =>
      val event = v2.ProjectRemoved(v.id.value)
      v2e.encode(v2.ProjectRemoved.SCHEMA$)(event)
    }

  given EventMessageDecoder[ProjectRemoved] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.ProjectRemoved.SCHEMA$
          qm.toMessage[v1.ProjectRemoved](schema)
            .map(_.map(e => ProjectRemoved(Id(e.id))))

        case SchemaVersion.V2 =>
          val schema = v2.ProjectRemoved.SCHEMA$
          qm.toMessage[v2.ProjectRemoved](schema)
            .map(_.map(e => ProjectRemoved(Id(e.id))))
    }
