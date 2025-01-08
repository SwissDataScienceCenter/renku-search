package io.renku.search.sentry

opaque type TagValue = String

object TagValue:
  val searchApi: TagValue = unsafe("search-api")
  val searchProvision: TagValue = unsafe("search-provision")

  def from(str: String): Either[String, TagValue] =
    if (str.length() > 200) Left(s"Tag name is too long (>200): ${str.length}")
    else if (str.matches("[^\n]+")) Right(str)
    else Left(s"Invalid tag value: $str")

  def unsafe(str: String): TagValue =
    from(str).fold(sys.error, identity)

  extension (self: TagValue) def value: String = self
