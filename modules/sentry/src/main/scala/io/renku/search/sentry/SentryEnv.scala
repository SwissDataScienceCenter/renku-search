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

  extension (self: SentryEnv) def value: String = self
