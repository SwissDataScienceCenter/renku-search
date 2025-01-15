package io.renku.search.authzed

opaque type BearerToken = String

object BearerToken:
  def apply(token: String): Either[String, BearerToken] =
    if (token.trim.isEmpty()) Left("BearerToken must not be empty")
    else Right(token)

  def unsafe(token: String): BearerToken =
    apply(token).fold(sys.error, identity)

  extension (self: BearerToken) def value: String = self
