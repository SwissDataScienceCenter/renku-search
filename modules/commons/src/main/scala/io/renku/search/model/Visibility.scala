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

import cats.kernel.Order

import io.bullet.borer.derivation.MapBasedCodecs.*
import io.bullet.borer.{Decoder, Encoder}

enum Visibility:
  lazy val name: String = productPrefix.toLowerCase
  case Public, Private

  override def toString(): String = name

object Visibility:
  given Order[Visibility] = Order.by(_.ordinal)
  given Encoder[Visibility] = Encoder.forString.contramap(_.name)
  given Decoder[Visibility] = Decoder.forString.mapEither(Visibility.fromString)

  def fromString(v: String): Either[String, Visibility] =
    Visibility.values
      .find(_.name.equalsIgnoreCase(v))
      .toRight(s"Invalid visibility: $v")

  def unsafeFromString(v: String): Visibility =
    fromString(v).fold(sys.error, identity)
