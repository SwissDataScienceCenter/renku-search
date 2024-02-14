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

package io.renku.search.query

import io.bullet.borer.{Decoder, Encoder}

enum Field:
  case ProjectId
  case Name
  case Slug
  case Visibility
  case Created
  case CreatedBy

  val name: String = Strings.lowerFirst(productPrefix)

object Field:
  given Encoder[Field] = Encoder.forString.contramap(_.name)
  given Decoder[Field] = Decoder.forString.mapEither(fromString)

  private[this] val allNames: String = Field.values.mkString(", ")

  def fromString(str: String): Either[String, Field] =
    Field.values
      .find(_.name.equalsIgnoreCase(str))
      .toRight(s"Invalid field: $str. Allowed are: $allNames")

  def unsafeFromString(str: String): Field =
    fromString(str).fold(sys.error, identity)
