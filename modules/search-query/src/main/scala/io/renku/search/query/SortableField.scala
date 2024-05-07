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

import cats.kernel.Order

import io.bullet.borer.{Decoder, Encoder}

enum SortableField:
  case Name
  case Created
  case Score

  val name: String = Strings.lowerFirst(productPrefix)

object SortableField:
  given Encoder[SortableField] = Encoder.forString.contramap(_.name)
  given Decoder[SortableField] = Decoder.forString.mapEither(fromString)
  given Order[SortableField] = Order.by(_.name)

  private val allNames: String = SortableField.values.map(_.name).mkString(", ")

  def fromString(str: String): Either[String, SortableField] =
    SortableField.values
      .find(_.name.equalsIgnoreCase(str))
      .toRight(s"Invalid field: $str. Allowed are: $allNames")

  def unsafeFromString(str: String): SortableField =
    fromString(str).fold(sys.error, identity)
