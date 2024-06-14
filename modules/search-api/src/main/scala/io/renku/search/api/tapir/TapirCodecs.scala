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

package io.renku.search.api.tapir

import io.renku.search.api.data.*
import io.renku.search.model.{EntityType, Id}
import io.renku.search.query.Query
import sttp.tapir.*

trait TapirCodecs:
  given Codec[String, Query, CodecFormat.TextPlain] =
    Codec.string.mapEither(Query.parse(_))(_.render)

  given Codec[String, EntityType, CodecFormat.TextPlain] =
    Codec.string.mapEither(EntityType.fromString(_))(_.name)

  given Codec[String, AuthToken.JwtToken, CodecFormat.TextPlain] =
    Codec.string.map(AuthToken.JwtToken(_))(_.render)

  given Codec[String, AuthToken.AnonymousId, CodecFormat.TextPlain] =
    Codec.string.map(s => AuthToken.AnonymousId(Id(s)))(_.render)

object TapirCodecs extends TapirCodecs
