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

opaque type SentryEnv = String

object SentryEnv:
  def fromString(str: String): Either[String, SentryEnv] =
    val sn = str.trim
    if (sn.isEmpty) Left("Empty sentry environment provided")
    else Right(sn)

  def unsafeFromString(str: String): SentryEnv =
    fromString(str).fold(sys.error, identity)

  val production: SentryEnv = "production"
  val dev: SentryEnv = "dev"

  extension (self: SentryEnv)
    def value: String = self
