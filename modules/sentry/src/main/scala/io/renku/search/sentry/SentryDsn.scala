package io.renku.search.sentry

opaque type SentryDsn = String

object SentryDsn:
  def fromString(str: String): Either[String, SentryDsn] =
    val sn = str.trim
    if (sn.isEmpty) Left("Empty sentry DSN provided")
    else Right(sn)

  def unsafeFromString(str: String): SentryDsn =
    fromString(str).fold(sys.error, identity)

  extension (self: SentryDsn) def value: String = self
