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

import io.bullet.borer.Encoder
import io.bullet.borer.Json
import sttp.tapir.Schema

trait SchemaSyntax:

  extension [T](self: Schema[T])
    def jsonExample[TT >: T](value: TT)(using Encoder[TT]): Schema[T] =
      self.encodedExample(Json.encode(value).toUtf8String)

object SchemaSyntax extends SchemaSyntax
