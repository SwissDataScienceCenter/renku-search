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

package io.renku.search.api.data

import io.renku.search.model.Id

sealed trait AuthToken:
  def render: String
  def fold[A](
      fa: AuthToken.JwtToken => A,
      fb: AuthToken.AnonymousId => A,
      fc: => A
  ): A

object AuthToken:
  case object None extends AuthToken:
    def render = ""
    def fold[A](
        fa: AuthToken.JwtToken => A,
        fb: AuthToken.AnonymousId => A,
        fc: => A
    ): A = fc

  final case class AnonymousId(anonId: Id) extends AuthToken:
    def render = anonId.value
    def fold[A](
        fa: AuthToken.JwtToken => A,
        fb: AuthToken.AnonymousId => A,
        fc: => A
    ): A = fb(this)

  final case class JwtToken(token: String) extends AuthToken:
    def render = token
    def fold[A](
        fa: AuthToken.JwtToken => A,
        fb: AuthToken.AnonymousId => A,
        fc: => A
    ): A = fa(this)
