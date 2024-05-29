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

import cats.Show
import cats.syntax.all.*

import io.renku.search.jwt.JwtBorer
import io.renku.search.model.Id
import io.renku.search.solr.SearchRole
import pdi.jwt.JwtClaim

sealed trait AuthContext:
  def isAnonymous: Boolean
  def isAuthenticated: Boolean = !isAnonymous
  def fold[A](
      fa: AuthContext.Admin => A,
      fb: AuthContext.Authenticated => A,
      fc: AuthContext.AnonymousId => A,
      fd: => A
  ): A
  def searchRole: SearchRole =
    fold(
      a => SearchRole.admin(a.userId),
      a => SearchRole.user(a.userId),
      _ => SearchRole.Anonymous,
      SearchRole.Anonymous
    )
  def render: String

object AuthContext:
  val anonymous: AuthContext = Anonymous
  def anonymousId(anonId: String): AuthContext = AnonymousId(Id(anonId))
  def authenticated(userId: Id): AuthContext = Authenticated(userId)
  def admin(userId: Id): AuthContext = Admin(userId)

  case object Anonymous extends AuthContext {
    val isAnonymous = true
    val render = ""
    def fold[A](
        fa: AuthContext.Admin => A,
        fb: AuthContext.Authenticated => A,
        fc: AuthContext.AnonymousId => A,
        fd: => A
    ): A = fd
  }

  final case class AnonymousId(anonId: Id) extends AuthContext {
    val isAnonymous = true
    def fold[A](
        fa: AuthContext.Admin => A,
        fb: AuthContext.Authenticated => A,
        fc: AuthContext.AnonymousId => A,
        fd: => A
    ): A = fc(this)
    val render = anonId.value
  }
  final case class Authenticated(userId: Id) extends AuthContext:
    val isAnonymous = false
    def fold[A](
        fa: AuthContext.Admin => A,
        fb: AuthContext.Authenticated => A,
        fc: AuthContext.AnonymousId => A,
        fd: => A
    ): A = fb(this)
    def render = JwtBorer.encode(JwtClaim(subject = userId.value.some))

  final case class Admin(userId: Id) extends AuthContext:
    val isAnonymous = false
    def fold[A](
        fa: AuthContext.Admin => A,
        fb: AuthContext.Authenticated => A,
        fc: AuthContext.AnonymousId => A,
        fd: => A
    ): A = fa(this)
    def render = JwtBorer.encode(JwtClaim(subject = userId.value.some))

  given Show[AuthContext] = Show.show {
    case Admin(id)         => s"Admin(${id.value})"
    case Authenticated(id) => s"Authenticated(${id.value})"
    case AnonymousId(id)   => s"AnonymousId(${id.value})"
    case Anonymous         => "Anonymous"
  }
