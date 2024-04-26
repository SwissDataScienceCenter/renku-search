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
import io.bullet.borer.{Codec, Decoder, Encoder}
import io.github.arainko.ducktape.*
import io.renku.search.borer.codecs.all.given

import java.time.Instant

object projects:
  opaque type Slug = String
  object Slug:
    def apply(v: String): Slug = v
    extension (self: Slug) def value: String = self
    given Transformer[String, Slug] = apply
    given Codec[Slug] = Codec.of[String]

  opaque type Repository = String
  object Repository:
    def apply(v: String): Repository = v
    extension (self: Repository) def value: String = self
    given Transformer[String, Repository] = apply
    given Codec[Repository] = Codec.of[String]

  opaque type Description = String
  object Description:
    def apply(v: String): Description = v
    def from(v: Option[String]): Option[Description] =
      v.flatMap {
        _.trim match {
          case "" => Option.empty[Description]
          case o  => Option(o)
        }
      }
    extension (self: Description) def value: String = self
    given Transformer[String, Description] = apply
    given Codec[Description] = Codec.of[String]

  opaque type CreationDate = Instant
  object CreationDate:
    def apply(v: Instant): CreationDate = v
    extension (self: CreationDate) def value: Instant = self
    given Transformer[Instant, CreationDate] = apply
    given Codec[CreationDate] = Codec.of[Instant]

  enum Visibility:
    lazy val name: String = productPrefix.toLowerCase
    case Public, Private

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
