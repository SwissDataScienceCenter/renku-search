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

package io.renku.search.sentry

opaque type TagName = String

object TagName:
  val service: TagName = unsafe("service")

  def from(str: String): Either[String, TagName] =
    if (str.length() > 32) Left(s"Tag name is too long (>32): ${str.length}")
    else if (str.matches("[a-zA-Z0-9_\\.:\\-]+")) Right(str)
    else Left(s"Invalid tag name: $str")

  def unsafe(str: String): TagName =
    from(str).fold(sys.error, identity)

  extension (self: TagName) def value: String = self
