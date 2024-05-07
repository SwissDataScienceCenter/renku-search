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

package io.renku.search.model

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

enum EntityType:
  case Project
  case User
  case Group

  def name: String = productPrefix

object EntityType:
  def fromString(str: String): Either[String, EntityType] =
    EntityType.values
      .find(_.name.equalsIgnoreCase(str))
      .toRight(s"Invalid entity type: $str")

  def unsafeFromString(str: String): EntityType =
    fromString(str).fold(sys.error, identity)

  given Encoder[EntityType] = Encoder.forString.contramap(_.name)
  given Decoder[EntityType] = Decoder.forString.mapEither(fromString)
