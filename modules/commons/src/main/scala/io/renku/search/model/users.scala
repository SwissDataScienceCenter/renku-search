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

object users:

  opaque type Id = String
  object Id:
    def apply(v: String): Id = v
    extension (self: Id) def value: String = self
    given Transformer[String, Id] = apply
    given Codec[Id] = Codec.bimap[String, Id](_.value, Id.apply)

  opaque type FirstName = String
  object FirstName:
    def apply(v: String): FirstName = v
    extension (self: FirstName) def value: String = self
    given Transformer[String, FirstName] = apply
    given Codec[FirstName] = Codec.bimap[String, FirstName](_.value, FirstName.apply)

  opaque type LastName = String
  object LastName:
    def apply(v: String): LastName = v
    extension (self: LastName) def value: String = self
    given Transformer[String, LastName] = apply
    given Codec[LastName] = Codec.bimap[String, LastName](_.value, LastName.apply)

  opaque type Email = String
  object Email:
    def apply(v: String): Email = v
    extension (self: Email) def value: String = self
    given Transformer[String, Email] = apply
    given Codec[Email] = Codec.bimap[String, Email](_.value, LastName.apply)
