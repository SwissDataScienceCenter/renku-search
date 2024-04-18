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

import io.bullet.borer.Codec
import io.github.arainko.ducktape.Transformer

object groups:

  opaque type Name = String
  object Name:
    def apply(v: String): Name = v
    extension (self: Name) def value: String = self
    given Transformer[String, Name] = apply
    given Codec[Name] = Codec.bimap[String, Name](_.value, Name.apply)

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
