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
