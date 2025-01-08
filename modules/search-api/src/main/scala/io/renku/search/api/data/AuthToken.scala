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
